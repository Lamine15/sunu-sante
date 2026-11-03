package sn.ugb.sunusante.dossier.modele;

import java.time.Instant;

public record Note(
        String id,
        String auteur,
        String contenu,
        Instant date) {
}
