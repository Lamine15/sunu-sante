package sn.ugb.sunusante.dossier.modele;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dossier médical d'un patient. Objet immuable : chaque modification produit
 * une nouvelle valeur, qui devient une nouvelle version répliquée par quorum.
 */
public record DossierMedical(
        String patientId,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        String pathologie,
        String medecinTraitant,
        List<EntreeMesure> mesures,
        List<Prescription> prescriptions,
        List<Note> notes,
        Instant creeLe,
        Instant modifieLe) {

    /** Taille maximale de l'historique de mesures conservé dans le dossier. */
    public static final int HISTORIQUE_MAX = 500;

    public DossierMedical {
        mesures = mesures == null ? List.of() : List.copyOf(mesures);
        prescriptions = prescriptions == null ? List.of() : List.copyOf(prescriptions);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static DossierMedical nouveau(String patientId, String nom, String prenom, LocalDate dateNaissance,
                                         String pathologie, String medecinTraitant, Instant maintenant) {
        return new DossierMedical(patientId, nom, prenom, dateNaissance, pathologie, medecinTraitant,
                List.of(), List.of(), List.of(), maintenant, maintenant);
    }

    /** Ajoute une mesure ; renvoie le dossier inchangé si elle est déjà présente (livraison en double). */
    public DossierMedical avecMesure(EntreeMesure mesure, Instant maintenant) {
        if (mesures.stream().anyMatch(m -> m.id().equals(mesure.id()))) {
            return this;
        }
        List<EntreeMesure> liste = new ArrayList<>(mesures);
        liste.add(mesure);
        if (liste.size() > HISTORIQUE_MAX) {
            liste = liste.subList(liste.size() - HISTORIQUE_MAX, liste.size());
        }
        return new DossierMedical(patientId, nom, prenom, dateNaissance, pathologie, medecinTraitant,
                liste, prescriptions, notes, creeLe, maintenant);
    }

    public DossierMedical avecPrescription(Prescription prescription, Instant maintenant) {
        List<Prescription> liste = new ArrayList<>(prescriptions);
        liste.add(prescription);
        return new DossierMedical(patientId, nom, prenom, dateNaissance, pathologie, medecinTraitant,
                mesures, liste, notes, creeLe, maintenant);
    }

    public DossierMedical avecNote(Note note, Instant maintenant) {
        List<Note> liste = new ArrayList<>(notes);
        liste.add(note);
        return new DossierMedical(patientId, nom, prenom, dateNaissance, pathologie, medecinTraitant,
                mesures, prescriptions, liste, creeLe, maintenant);
    }
}
