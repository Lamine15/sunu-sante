package sn.ugb.sunusante.dossier.quorum;

import java.util.Optional;

import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;

/** Une des N répliques du dossier médical, locale ou distante. */
public interface Replique {

    String nom();

    /** @return la version détenue par la réplique, vide si elle ne connaît pas ce dossier */
    Optional<DossierVersionne> lire(String patientId);

    ReponseReplique ecrire(DossierVersionne dossier);
}
