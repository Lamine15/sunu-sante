package sn.ugb.sunusante.notification.modele;

import java.time.Instant;

/** Événement « Alerte Critique » reçu du broker (contrat publié par service-analyse-temps-reel). */
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
