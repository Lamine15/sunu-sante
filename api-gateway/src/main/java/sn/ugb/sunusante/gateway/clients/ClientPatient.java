package sn.ugb.sunusante.gateway.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/** Client REST du service-patient (profils et mesures). */
@Component
public class ClientPatient {

    private final RestClient client;

    public ClientPatient(@Value("${sunu.patient.url}") String url, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(1000);
        fabrique.setReadTimeout(3000);
        this.client = builder.clone().baseUrl(url).requestFactory(fabrique).build();
    }

    public JsonNode inscrire(JsonNode patient) {
        return client.post().uri("/patients").contentType(MediaType.APPLICATION_JSON).body(patient)
                .retrieve().body(JsonNode.class);
    }

    public JsonNode lister() {
        return client.get().uri("/patients").retrieve().body(JsonNode.class);
    }

    public JsonNode lire(String id) {
        return client.get().uri("/patients/{id}", id).retrieve().body(JsonNode.class);
    }

    public void supprimer(String id) {
        client.delete().uri("/patients/{id}", id).retrieve().toBodilessEntity();
    }

    public JsonNode envoyerMesure(JsonNode mesure) {
        return client.post().uri("/mesures").contentType(MediaType.APPLICATION_JSON).body(mesure)
                .retrieve().body(JsonNode.class);
    }

    public JsonNode etatTampon() {
        return client.get().uri("/mesures/tampon").retrieve().body(JsonNode.class);
    }
}
