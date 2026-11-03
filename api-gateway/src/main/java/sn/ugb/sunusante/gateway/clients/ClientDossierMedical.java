package sn.ugb.sunusante.gateway.clients;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Client synchrone du service-dossier-medical, protégé par le disjoncteur « dossierMedical ».
 *
 * <p>Les trois nœuds sont équivalents (pas de leader) : les requêtes leur sont réparties à tour
 * de rôle, et un nœud injoignable est sauté au profit du suivant. Si le service lui-même échoue
 * (quorum non atteint, surcharge), l'erreur est comptée par le disjoncteur ; au-delà du seuil,
 * il s'ouvre et les appels suivants échouent immédiatement sans solliciter le service.</p>
 */
@Component
public class ClientDossierMedical {

    private static final Logger log = LoggerFactory.getLogger(ClientDossierMedical.class);

    private final List<RestClient> noeuds;
    private final AtomicInteger suivant = new AtomicInteger();
    private final CircuitBreaker disjoncteur;

    public ClientDossierMedical(@Value("${sunu.dossier.noeuds}") List<String> urls, RestClient.Builder builder,
                                CircuitBreakerRegistry disjoncteurs) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(1000);
        fabrique.setReadTimeout(5000);
        this.noeuds = urls.stream().map(String::trim)
                .map(url -> builder.clone().baseUrl(url).requestFactory(fabrique).build()).toList();
        this.disjoncteur = disjoncteurs.circuitBreaker("dossierMedical");
        this.disjoncteur.getEventPublisher().onStateTransition(e ->
                log.warn("Disjoncteur dossierMedical : {}", e.getStateTransition()));
    }

    public JsonNode creer(JsonNode dossier) {
        return protege(noeud -> noeud.post().uri("/dossiers").contentType(MediaType.APPLICATION_JSON)
                .body(dossier).retrieve().body(JsonNode.class));
    }

    public JsonNode lire(String patientId) {
        return protege(noeud -> noeud.get().uri("/dossiers/{id}", patientId).retrieve().body(JsonNode.class));
    }

    public JsonNode ajouter(String patientId, String rubrique, JsonNode contenu) {
        return protege(noeud -> noeud.post().uri("/dossiers/{id}/{rubrique}", patientId, rubrique)
                .contentType(MediaType.APPLICATION_JSON).body(contenu).retrieve().body(JsonNode.class));
    }

    public JsonNode repliques(String patientId) {
        return protege(noeud -> noeud.get().uri("/dossiers/{id}/repliques", patientId)
                .retrieve().body(JsonNode.class));
    }

    public CircuitBreaker disjoncteur() {
        return disjoncteur;
    }

    private JsonNode protege(Function<RestClient, JsonNode> appel) {
        return disjoncteur.executeSupplier(() -> appelerUnNoeud(appel));
    }

    private JsonNode appelerUnNoeud(Function<RestClient, JsonNode> appel) {
        ResourceAccessException derniere = null;
        for (int i = 0; i < noeuds.size(); i++) {
            RestClient noeud = noeuds.get(Math.floorMod(suivant.getAndIncrement(), noeuds.size()));
            try {
                return appel.apply(noeud);
            } catch (ResourceAccessException e) {
                // Nœud injoignable (arrêté, réseau coupé) : un autre nœud peut coordonner le quorum.
                derniere = e;
            }
        }
        throw derniere;
    }
}
