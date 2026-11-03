package sn.ugb.sunusante.patient.modele;

import java.time.Instant;

/** Mesure d'un capteur, telle que publiée dans le broker. */
public record Mesure(
        String id,
        String patientId,
        String type,
        double valeur,
        String unite,
        Instant horodatage) {
}
