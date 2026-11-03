package sn.ugb.sunusante.dossier.quorum;

import java.util.Optional;

import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;
import sn.ugb.sunusante.dossier.stockage.StockageLocal;

/** Le nœud courant participe au quorum comme les autres, sans passer par le réseau. */
public class RepliqueLocale implements Replique {

    private final String nom;
    private final StockageLocal stockage;

    public RepliqueLocale(String nom, StockageLocal stockage) {
        this.nom = nom;
        this.stockage = stockage;
    }

    @Override
    public String nom() {
        return nom;
    }

    @Override
    public Optional<DossierVersionne> lire(String patientId) {
        return stockage.lire(patientId);
    }

    @Override
    public ReponseReplique ecrire(DossierVersionne dossier) {
        return stockage.appliquer(dossier);
    }
}
