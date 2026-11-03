package sn.ugb.sunusante.dossier.quorum;

/**
 * Une écriture concurrente, coordonnée par un autre nœud, a produit une version plus récente
 * avant que la nôtre n'atteigne W répliques. Le client doit relire puis réessayer.
 */
public class ConflitEcritureException extends RuntimeException {

    public ConflitEcritureException(String patientId) {
        super("Modification concurrente du dossier " + patientId + " : relisez le dossier puis réessayez");
    }
}
