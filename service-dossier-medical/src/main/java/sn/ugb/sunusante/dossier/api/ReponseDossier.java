package sn.ugb.sunusante.dossier.api;

import java.util.List;

import sn.ugb.sunusante.dossier.modele.DossierMedical;
import sn.ugb.sunusante.dossier.quorum.CoordinateurQuorum.ResultatQuorum;

/** Dossier renvoyé au client, accompagné des informations de quorum utiles à la démonstration. */
public record ReponseDossier(
        DossierMedical dossier,
        long version,
        String ecritPar,
        String coordinateur,
        String quorum,
        List<String> repliquesConsultees) {

    static ReponseDossier depuis(ResultatQuorum resultat, String coordinateur, String quorum) {
        return new ReponseDossier(resultat.dossier().dossier(), resultat.dossier().version(),
                resultat.dossier().noeudAuteur(), coordinateur, quorum, resultat.repliques());
    }
}
