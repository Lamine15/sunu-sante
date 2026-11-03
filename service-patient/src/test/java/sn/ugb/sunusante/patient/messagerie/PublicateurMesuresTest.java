package sn.ugb.sunusante.patient.messagerie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import sn.ugb.sunusante.patient.modele.Mesure;

class PublicateurMesuresTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final SimpleMeterRegistry registre = new SimpleMeterRegistry();
    private final PublicateurMesures publicateur = new PublicateurMesures(rabbit,
            new ProprietesTampon(3, Duration.ZERO, Duration.ofMillis(10), Duration.ofSeconds(1)), registre);

    private static Mesure mesure(String id) {
        return new Mesure(id, "PAT-1", "RYTHME_CARDIAQUE", 120, "bpm", Instant.now());
    }

    @Test
    void lesMesuresSontMisesEnTamponPuisReemisesQuandLeBrokerRevient() {
        when(rabbit.invoke(any())).thenThrow(new AmqpConnectException(new java.net.ConnectException("refusé")));

        assertThat(publicateur.publier(mesure("m1"))).isEqualTo(PublicateurMesures.Statut.EN_TAMPON);
        assertThat(publicateur.publier(mesure("m2"))).isEqualTo(PublicateurMesures.Statut.EN_TAMPON);
        publicateur.vider();
        assertThat(registre.get("mesures.en.tampon").gauge().value()).isEqualTo(2);

        doReturn(Boolean.TRUE).when(rabbit).invoke(any());
        publicateur.vider();

        assertThat(registre.get("mesures.en.tampon").gauge().value()).isZero();
        assertThat(registre.get("mesures.capteurs.publiees").tag("mode", "tampon").counter().count()).isEqualTo(2);
    }

    @Test
    void leTamponPleinEcarteLaMesureLaPlusAncienne() {
        when(rabbit.invoke(any())).thenThrow(new AmqpConnectException(new java.net.ConnectException("refusé")));

        for (int i = 0; i < 5; i++) {
            publicateur.publier(mesure("m" + i));
        }

        assertThat(registre.get("mesures.en.tampon").gauge().value()).isEqualTo(3);
        assertThat(registre.get("mesures.perdues").counter().count()).isEqualTo(2);
    }

    @Test
    void leDelaiDoubleAChaqueEchecJusquauPlafond() {
        assertThat(PublicateurMesures.delaiBackoff(1, 1000, 60_000)).isEqualTo(1000);
        assertThat(PublicateurMesures.delaiBackoff(2, 1000, 60_000)).isEqualTo(2000);
        assertThat(PublicateurMesures.delaiBackoff(4, 1000, 60_000)).isEqualTo(8000);
        assertThat(PublicateurMesures.delaiBackoff(10, 1000, 60_000)).isEqualTo(60_000);
        assertThat(PublicateurMesures.delaiBackoff(500, 1000, 60_000)).isEqualTo(60_000);
    }
}
