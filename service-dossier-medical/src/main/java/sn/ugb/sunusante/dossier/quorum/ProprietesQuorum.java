package sn.ugb.sunusante.dossier.quorum;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du quorum. N n'est pas configuré : il vaut le nombre de pairs + 1 (le nœud courant).
 */
@ConfigurationProperties(prefix = "sunu.quorum")
public record ProprietesQuorum(
        String noeudId,
        List<String> pairs,
        int w,
        int r,
        Duration delaiReplique) {

    public ProprietesQuorum {
        pairs = pairs == null ? List.of()
                : pairs.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
        if (delaiReplique == null) {
            delaiReplique = Duration.ofSeconds(2);
        }
    }
}
