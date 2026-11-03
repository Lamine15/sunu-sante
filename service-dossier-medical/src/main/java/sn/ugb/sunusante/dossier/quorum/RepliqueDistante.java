package sn.ugb.sunusante.dossier.quorum;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;

/** Réplique joignable par l'API interne {@code /interne/dossiers} d'un autre nœud. */
public class RepliqueDistante implements Replique {

    private final String url;
    private final RestClient client;

    public RepliqueDistante(String url, RestClient.Builder builder, Duration delai) {
        this.url = url;
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout((int) Math.min(delai.toMillis(), 1000));
        fabrique.setReadTimeout((int) delai.toMillis());
        this.client = builder.baseUrl(url).requestFactory(fabrique).build();
    }

    @Override
    public String nom() {
        return url.replaceFirst("^https?://", "");
    }

    @Override
    public Optional<DossierVersionne> lire(String patientId) {
        return client.get()
                .uri("/interne/dossiers/{id}", patientId)
                .exchange((requete, reponse) -> {
                    if (reponse.getStatusCode().value() == 404) {
                        return Optional.empty();
                    }
                    if (reponse.getStatusCode().isError()) {
                        throw new IllegalStateException(nom() + " a répondu " + reponse.getStatusCode());
                    }
                    return Optional.ofNullable(reponse.bodyTo(DossierVersionne.class));
                });
    }

    @Override
    public ReponseReplique ecrire(DossierVersionne dossier) {
        return client.put()
                .uri("/interne/dossiers/{id}", dossier.dossier().patientId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(dossier)
                .retrieve()
                .body(ReponseReplique.class);
    }
}
