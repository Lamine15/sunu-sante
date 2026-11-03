package sn.ugb.sunusante.dossier.api;

public class DossierExistantException extends RuntimeException {

    public DossierExistantException(String patientId) {
        super("Un dossier médical existe déjà pour le patient " + patientId);
    }
}
