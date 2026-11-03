package sn.ugb.sunusante.analyse.dossier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import sn.ugb.sunusante.analyse.modele.Mesure;

/**
 * Client synchrone du service-dossier-medical. Les requêtes sont réparties entre les trois
 * nœuds (n'importe lequel peut coordonner le quorum) ; un nœud injoignable est sauté.
 * L'ensemble est protégé par le disjoncteur « dossierMedical ».
 */
@Component
public class ClientDossierMedical {

    private static final Logger log = LoggerFactory.getLogger(ClientDossierMedical.class);

    private final List<RestClient> noeuds;
    private final AtomicInteger suivant = new AtomicInteger();
    private final CircuitBreaker disjoncteur;
    private final Counter nonConsignees;

    public ClientDossierMedical(@Value("${sunu.dossier.noeuds}") List<String> urls, RestClient.Builder builder,
                                CircuitBreakerRegistry disjoncteurs, MeterRegistry registre) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(1000);
        fabrique.setReadTimeout(3000);
        this.noeuds = urls.stream().map(String::trim)
                .map(url -> builder.clone().baseUrl(url).requestFactory(fabrique).build()).toList();
        this.disjoncteur = disjoncteurs.circuitBreaker("dossierMedical");
        this.disjoncteur.getEventPublisher().onStateTransition(e ->
                log.warn("Disjoncteur dossierMedical : {}", e.getStateTransition()));
        this.nonConsignees = Counter.builder("anomalies.non.consignees")
                .description("Anomalies non écrites dans le dossier (service indisponible)").register(registre);
    }

    /**
     * Consigne la mesure anormale dans l'historique du dossier. L'alerte étant déjà partie,
     * un échec ici est journalisé mais ne bloque pas le traitement du flux.
     */
    public void consignerAnomalie(Mesure mesure, String commentaire) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("id", mesure.id());
        corps.put("type", mesure.type());
        corps.put("valeur", mesure.valeur());
        corps.put("unite", mesure.unite());
        corps.put("horodatage", mesure.horodatage());
        corps.put("anomalie", true);
        corps.put("commentaire", commentaire);
        try {
            disjoncteur.executeRunnable(() -> appeler(noeud -> noeud.post()
                    .uri("/dossiers/{id}/mesures", mesure.patientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .toBodilessEntity()));
            log.info("Mesure anormale consignée dans le dossier médical du patient {}", mesure.patientId());
        } catch (CallNotPermittedException e) {
            nonConsignees.increment();
            log.warn("Disjoncteur ouvert : anomalie du patient {} non consignée (l'alerte est bien partie)",
                    mesure.patientId());
        } catch (RuntimeException e) {
            nonConsignees.increment();
            log.warn("Consignation impossible pour le patient {} : {}", mesure.patientId(), e.getMessage());
        }
    }

    private <T> T appeler(java.util.function.Function<RestClient, T> appel) {
        ResourceAccessException derniere = null;
        for (int i = 0; i < noeuds.size(); i++) {
            RestClient noeud = noeuds.get(Math.floorMod(suivant.getAndIncrement(), noeuds.size()));
            try {
                return appel.apply(noeud);
            } catch (ResourceAccessException e) {
                derniere = e;
            }
        }
        throw derniere;
    }
}
