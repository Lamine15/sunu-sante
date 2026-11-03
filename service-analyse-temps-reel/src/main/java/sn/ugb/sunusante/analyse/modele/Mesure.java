package sn.ugb.sunusante.analyse.modele;

import java.time.Instant;

/** Mesure brute reçue du broker (contrat du flux publié par service-patient). */
public record Mesure(
        String id,
        String patientId,
        String type,
        double valeur,
        String unite,
        Instant horodatage) {
}
