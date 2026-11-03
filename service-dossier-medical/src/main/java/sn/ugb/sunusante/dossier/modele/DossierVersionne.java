package sn.ugb.sunusante.dossier.modele;

import java.time.Instant;

/**
 * Valeur stockée par chaque réplique : le dossier, son numéro de version et le
 * nœud qui l'a écrite. Le couple (version, noeudAuteur) définit un ordre total
 * entre versions, ce qui permet à une lecture de quorum de choisir la plus récente
 * sans horloge partagée ni leader.
 */
public record DossierVersionne(
        DossierMedical dossier,
        long version,
        String noeudAuteur,
        Instant horodatage) {

    /** Vrai si cette version doit remplacer {@code autre} (version supérieure, ou égale avec un auteur supérieur). */
    public boolean estPlusRecenteQue(DossierVersionne autre) {
        if (autre == null) {
            return true;
        }
        if (version != autre.version) {
            return version > autre.version;
        }
        return noeudAuteur.compareTo(autre.noeudAuteur) > 0;
    }

    public boolean memeVersionQue(DossierVersionne autre) {
        return autre != null && version == autre.version && noeudAuteur.equals(autre.noeudAuteur);
    }
}
