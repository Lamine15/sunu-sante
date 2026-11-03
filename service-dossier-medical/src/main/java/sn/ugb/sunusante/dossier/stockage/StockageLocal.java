package sn.ugb.sunusante.dossier.stockage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;
import sn.ugb.sunusante.dossier.quorum.ProprietesQuorum;

/**
 * Stockage de la réplique locale : une table en mémoire, doublée d'un fichier JSON
 * par dossier pour survivre au redémarrage du conteneur. Une écriture n'est acquittée
 * qu'après avoir été rendue durable sur disque.
 */
@Component
public class StockageLocal {

    private static final Logger log = LoggerFactory.getLogger(StockageLocal.class);

    private final Map<String, DossierVersionne> dossiers = new ConcurrentHashMap<>();
    private final Path repertoire;
    private final ObjectMapper mapper;
    private final String noeudId;

    public StockageLocal(@Value("${sunu.stockage.repertoire}") String repertoire, ObjectMapper mapper,
                         ProprietesQuorum proprietes, MeterRegistry registre) throws IOException {
        this.repertoire = Path.of(repertoire);
        this.mapper = mapper;
        this.noeudId = proprietes.noeudId();
        Files.createDirectories(this.repertoire);
        charger();
        Gauge.builder("dossiers.stockes.localement", dossiers, Map::size)
                .description("Nombre de dossiers détenus par cette réplique")
                .register(registre);
    }

    public Optional<DossierVersionne> lire(String patientId) {
        return Optional.ofNullable(dossiers.get(patientId));
    }

    /**
     * Applique une version reçue d'un coordinateur. La réplique n'accepte que les versions
     * plus récentes que la sienne : les répliques convergent donc toutes vers la même valeur,
     * quel que soit l'ordre d'arrivée des écritures.
     */
    public synchronized ReponseReplique appliquer(DossierVersionne entrante) {
        String id = entrante.dossier().patientId();
        DossierVersionne actuelle = dossiers.get(id);
        if (entrante.memeVersionQue(actuelle)) {
            return new ReponseReplique(noeudId, true, actuelle.version(), actuelle.noeudAuteur());
        }
        if (!entrante.estPlusRecenteQue(actuelle)) {
            log.warn("Écriture de {} v{} refusée : version v{} ({}) déjà présente",
                    id, entrante.version(), actuelle.version(), actuelle.noeudAuteur());
            return new ReponseReplique(noeudId, false, actuelle.version(), actuelle.noeudAuteur());
        }
        persister(entrante);
        dossiers.put(id, entrante);
        log.info("Dossier {} enregistré localement en v{} (écrit par {})",
                id, entrante.version(), entrante.noeudAuteur());
        return new ReponseReplique(noeudId, true, entrante.version(), entrante.noeudAuteur());
    }

    private void persister(DossierVersionne dossier) {
        Path cible = repertoire.resolve(dossier.dossier().patientId() + ".json");
        Path temporaire = repertoire.resolve(dossier.dossier().patientId() + ".json.tmp");
        try {
            mapper.writeValue(temporaire.toFile(), dossier);
            Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Persistance impossible du dossier " + dossier.dossier().patientId(), e);
        }
    }

    private void charger() throws IOException {
        try (Stream<Path> fichiers = Files.list(repertoire)) {
            fichiers.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                try {
                    DossierVersionne d = mapper.readValue(f.toFile(), DossierVersionne.class);
                    dossiers.put(d.dossier().patientId(), d);
                } catch (IOException e) {
                    log.error("Fichier de dossier illisible ignoré : {}", f, e);
                }
            });
        }
        log.info("{} dossier(s) rechargé(s) depuis {}", dossiers.size(), repertoire.toAbsolutePath());
    }
}
