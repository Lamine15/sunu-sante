package sn.ugb.sunusante.analyse.modele;

import java.time.Instant;

/** Événement « Alerte Critique » publié dans le broker à destination du service de notification. */
public record AlerteCritique(
        String id,
        String mesureId,
        String patientId,
        String type,
        double valeur,
        String unite,
        String regle,
        String message,
        Instant horodatageMesure,
        Instant detecteeLe) {
}
