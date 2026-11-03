package sn.ugb.sunusante.gateway.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import sn.ugb.sunusante.gateway.clients.ClientDossierMedical;
import sn.ugb.sunusante.gateway.clients.ClientPatient;

/** Point d'entrée unique de l'administration (console du médecin, scripts de test). */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private static final List<String> CHAMPS_DOSSIER =
            List.of("nom", "prenom", "dateNaissance", "pathologie", "medecinTraitant");

    private final ClientPatient patients;
    private final ClientDossierMedical dossiers;
    private final ObjectMapper mapper;

    public GatewayController(ClientPatient patients, ClientDossierMedical dossiers, ObjectMapper mapper) {
        this.patients = patients;
        this.dossiers = dossiers;
        this.mapper = mapper;
    }

    /**
     * Inscription d'un patient : profil dans service-patient, puis dossier dans service-dossier-medical.
     * Si le dossier ne peut pas être créé, le profil est supprimé (action compensatoire) pour ne pas
     * laisser un patient sans dossier.
     */
    @PostMapping("/patients")
    public ResponseEntity<JsonNode> inscrire(@RequestBody JsonNode requete) {
        JsonNode patient = patients.inscrire(requete);
        String id = patient.get("id").asText();

        ObjectNode dossier = mapper.createObjectNode().put("patientId", id);
        CHAMPS_DOSSIER.forEach(champ -> {
            if (patient.hasNonNull(champ)) {
                dossier.set(champ, patient.get(champ));
            }
        });
        try {
            JsonNode dossierCree = dossiers.creer(dossier);
            ObjectNode reponse = mapper.createObjectNode();
            reponse.set("patient", patient);
            reponse.set("dossier", dossierCree);
            log.info("Patient {} inscrit, dossier créé en v{}", id, dossierCree.path("version").asLong());
            return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
        } catch (RuntimeException e) {
            log.warn("Dossier du patient {} non créé ({}) : inscription annulée", id, e.getMessage());
            try {
                patients.supprimer(id);
            } catch (RuntimeException suppression) {
                log.error("Annulation de l'inscription de {} impossible : {}", id, suppression.getMessage());
            }
            throw e;
        }
    }

    @GetMapping("/patients")
    public JsonNode lister() {
        return patients.lister();
    }

    @GetMapping("/patients/{id}")
    public JsonNode patient(@PathVariable String id) {
        return patients.lire(id);
    }

    @GetMapping("/patients/{id}/dossier")
    public JsonNode dossier(@PathVariable String id) {
        return dossiers.lire(id);
    }

    @GetMapping("/patients/{id}/dossier/repliques")
    public JsonNode repliques(@PathVariable String id) {
        return dossiers.repliques(id);
    }

    @PostMapping("/patients/{id}/prescriptions")
    public JsonNode prescrire(@PathVariable String id, @RequestBody JsonNode prescription) {
        return dossiers.ajouter(id, "prescriptions", prescription);
    }

    @PostMapping("/patients/{id}/notes")
    public JsonNode annoter(@PathVariable String id, @RequestBody JsonNode note) {
        return dossiers.ajouter(id, "notes", note);
    }

    /** Relais vers le point de réception des capteurs, pratique pour la console de démonstration. */
    @PostMapping("/mesures")
    public ResponseEntity<JsonNode> mesure(@RequestBody JsonNode mesure) {
        return ResponseEntity.accepted().body(patients.envoyerMesure(mesure));
    }

    /** État des mécanismes de résilience : disjoncteur de la gateway et tampon du service-patient. */
    @GetMapping("/systeme/etat")
    public JsonNode etat() {
        CircuitBreaker disjoncteur = dossiers.disjoncteur();
        CircuitBreaker.Metrics m = disjoncteur.getMetrics();
        ObjectNode etat = mapper.createObjectNode();
        etat.putObject("disjoncteurDossierMedical")
                .put("etat", disjoncteur.getState().name())
                .put("tauxEchecPourcent", m.getFailureRate())
                .put("tauxAppelsLentsPourcent", m.getSlowCallRate())
                .put("appelsDansLaFenetre", m.getNumberOfBufferedCalls())
                .put("appelsEchoues", m.getNumberOfFailedCalls())
                .put("appelsRefusesDisjoncteurOuvert", m.getNumberOfNotPermittedCalls());
        try {
            etat.set("tamponServicePatient", patients.etatTampon());
        } catch (RuntimeException e) {
            etat.put("tamponServicePatient", "injoignable : " + e.getMessage());
        }
        return etat;
    }
}
