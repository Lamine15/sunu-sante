package sn.ugb.sunusante.dossier.modele;

import java.time.Instant;

/** Mesure enregistrée dans l'historique du dossier (en pratique, les mesures anormales). */
public record EntreeMesure(
        String id,
        String type,
        double valeur,
        String unite,
        Instant horodatage,
        boolean anomalie,
        String commentaire) {
}
