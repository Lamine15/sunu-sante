package sn.ugb.sunusante.patient.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sn.ugb.sunusante.patient.messagerie.PublicateurMesures;
import sn.ugb.sunusante.patient.modele.Mesure;
import sn.ugb.sunusante.patient.modele.TypeMesure;
import sn.ugb.sunusante.patient.stockage.RepertoirePatients;

/**
 * Point de réception des capteurs. La mesure est acceptée (202) dès qu'elle est publiée
 * ou mise en tampon : le capteur n'attend jamais l'analyse.
 */
@RestController
@RequestMapping("/mesures")
public class MesureController {

    public record RequeteMesure(
            @NotBlank String patientId,
            @NotNull TypeMesure type,
            @NotNull Double valeur,
            Instant horodatage) {
    }

    private final RepertoirePatients repertoire;
    private final PublicateurMesures publicateur;

    public MesureController(RepertoirePatients repertoire, PublicateurMesures publicateur) {
        this.repertoire = repertoire;
        this.publicateur = publicateur;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recevoir(@Valid @RequestBody RequeteMesure requete) {
        if (repertoire.trouver(requete.patientId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Patient inconnu : " + requete.patientId()));
        }
        Mesure mesure = new Mesure(UUID.randomUUID().toString(), requete.patientId(), requete.type().name(),
                requete.valeur(), requete.type().unite(),
                requete.horodatage() != null ? requete.horodatage() : Instant.now());

        PublicateurMesures.Statut statut = publicateur.publier(mesure);

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("mesureId", mesure.id());
        corps.put("statut", statut);
        corps.putAll(publicateur.etat());
        return ResponseEntity.accepted().body(corps);
    }

    @GetMapping("/tampon")
    public Map<String, Object> tampon() {
        return publicateur.etat();
    }
}
