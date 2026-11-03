package sn.ugb.sunusante.patient.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import sn.ugb.sunusante.patient.modele.Patient;
import sn.ugb.sunusante.patient.stockage.RepertoirePatients;

@RestController
@RequestMapping("/patients")
public class PatientController {

    public record NouveauPatient(
            @NotBlank String nom,
            @NotBlank String prenom,
            LocalDate dateNaissance,
            String telephone,
            String pathologie,
            String medecinTraitant) {
    }

    private final RepertoirePatients repertoire;

    public PatientController(RepertoirePatients repertoire) {
        this.repertoire = repertoire;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Patient inscrire(@Valid @RequestBody NouveauPatient requete) {
        String id = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Patient patient = new Patient(id, requete.nom(), requete.prenom(), requete.dateNaissance(),
                requete.telephone(), requete.pathologie(), requete.medecinTraitant(), Instant.now());
        repertoire.enregistrer(patient);
        return patient;
    }

    @GetMapping
    public Collection<Patient> lister() {
        return repertoire.tous();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> lire(@PathVariable String id) {
        return ResponseEntity.of(repertoire.trouver(id));
    }

    /** Utilisé par l'API Gateway pour annuler une inscription dont le dossier n'a pas pu être créé. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        return repertoire.supprimer(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
