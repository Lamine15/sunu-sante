package sn.ugb.sunusante.dossier.modele;

import java.time.Instant;

public record Prescription(
        String id,
        String medicament,
        String posologie,
        String prescripteur,
        Instant date) {
}
