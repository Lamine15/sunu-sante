package sn.ugb.sunusante.dossier.api;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Corps des requêtes de l'API publique. */
public final class Requetes {

    public static final String MOTIF_ID = "[A-Za-z0-9_-]{1,64}";

    private Requetes() {
    }

    public record NouveauDossier(
            @NotBlank @Pattern(regexp = MOTIF_ID) String patientId,
            @NotBlank String nom,
            @NotBlank String prenom,
            LocalDate dateNaissance,
            String pathologie,
            String medecinTraitant) {
    }

    public record NouvelleMesure(
            String id,
            @NotBlank String type,
            @NotNull Double valeur,
            String unite,
            Instant horodatage,
            boolean anomalie,
            String commentaire) {
    }

    public record NouvellePrescription(
            @NotBlank String medicament,
            @NotBlank String posologie,
            @NotBlank String prescripteur) {
    }

    public record NouvelleNote(
            @NotBlank String auteur,
            @NotBlank String contenu) {
    }
}
