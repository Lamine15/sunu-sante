package sn.ugb.sunusante.analyse.regles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import sn.ugb.sunusante.analyse.dossier.ClientDossierMedical;
import sn.ugb.sunusante.analyse.messagerie.ConfigurationMessagerie;
import sn.ugb.sunusante.analyse.modele.AlerteCritique;
import sn.ugb.sunusante.analyse.modele.Mesure;
import sn.ugb.sunusante.analyse.regles.MoteurRegles.Anomalie;

/**
 * Abonné au flux des mesures brutes. Pour chaque anomalie, publie un événement
 * « Alerte Critique » puis consigne la mesure anormale dans le dossier médical.
 */
@Component
public class AnalyseurMesures {

    private static final Logger log = LoggerFactory.getLogger(AnalyseurMesures.class);

    private final MoteurRegles moteur;
    private final RabbitTemplate rabbit;
    private final ClientDossierMedical dossiers;
    private final MeterRegistry registre;
    private final Timer delaiDetection;

    public AnalyseurMesures(MoteurRegles moteur, RabbitTemplate rabbit, ClientDossierMedical dossiers,
                            MeterRegistry registre) {
        this.moteur = moteur;
        this.rabbit = rabbit;
        this.dossiers = dossiers;
        this.registre = registre;
        this.delaiDetection = Timer.builder("alertes.delai.detection")
                .description("Temps entre la prise de mesure et la publication de l'alerte")
                .register(registre);
    }

    @RabbitListener(queues = ConfigurationMessagerie.FILE_MESURES)
    public void analyser(Mesure mesure) {
        compteur("mesures.capteurs.traitees", mesure.type()).increment();
        Optional<Anomalie> anomalie = moteur.evaluer(mesure);
        if (anomalie.isEmpty()) {
            log.info("Mesure traitée : patient {}, {} = {} {} -> normale",
                    mesure.patientId(), mesure.type(), mesure.valeur(), mesure.unite());
            return;
        }

        Instant maintenant = Instant.now();
        AlerteCritique alerte = new AlerteCritique(UUID.randomUUID().toString(), mesure.id(), mesure.patientId(),
                mesure.type(), mesure.valeur(), mesure.unite(), anomalie.get().regle(), anomalie.get().message(),
                mesure.horodatage(), maintenant);
        rabbit.convertAndSend(ConfigurationMessagerie.ECHANGE_ALERTES,
                ConfigurationMessagerie.CLE_ALERTE_CRITIQUE, alerte);
        compteur("anomalies.detectees", mesure.type()).increment();
        if (mesure.horodatage() != null) {
            delaiDetection.record(Duration.between(mesure.horodatage(), maintenant));
        }
        log.warn("ANOMALIE patient {} : {} -> événement « Alerte Critique » {} publié",
                mesure.patientId(), anomalie.get().message(), alerte.id());

        // Écriture synchrone dans la source de vérité, protégée par un disjoncteur.
        dossiers.consignerAnomalie(mesure, anomalie.get().message());
    }

    private Counter compteur(String nom, String type) {
        return Counter.builder(nom).tag("type", type).register(registre);
    }
}
