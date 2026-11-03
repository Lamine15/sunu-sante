package sn.ugb.sunusante.dossier.modele;

/** Accusé d'une réplique après une demande d'écriture. */
public record ReponseReplique(
        String noeud,
        boolean acceptee,
        long versionStockee,
        String auteurStocke) {
}
