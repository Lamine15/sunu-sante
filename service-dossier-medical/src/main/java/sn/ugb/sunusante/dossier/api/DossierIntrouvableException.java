package sn.ugb.sunusante.dossier.api;

public class DossierIntrouvableException extends RuntimeException {

    public DossierIntrouvableException(String patientId) {
        super("Aucun dossier médical pour le patient " + patientId);
    }
}
