package sn.ugb.sunusante.dossier.quorum;

import java.util.List;

/** Moins de W (écriture) ou de R (lecture) répliques ont répondu dans le délai imparti. */
public class QuorumNonAtteintException extends RuntimeException {

    private final List<String> echecs;

    public QuorumNonAtteintException(String operation, int obtenu, int requis, List<String> echecs) {
        super("Quorum de " + operation + " non atteint : " + obtenu + " réplique(s) sur " + requis + " requises");
        this.echecs = List.copyOf(echecs);
    }

    public List<String> getEchecs() {
        return echecs;
    }
}
