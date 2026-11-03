package sn.ugb.sunusante.patient.stockage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import sn.ugb.sunusante.patient.modele.Patient;

/** Profils patients en mémoire, sauvegardés dans un fichier JSON pour survivre aux redémarrages. */
@Component
public class RepertoirePatients {

    private static final Logger log = LoggerFactory.getLogger(RepertoirePatients.class);

    private final Map<String, Patient> patients = new ConcurrentHashMap<>();
    private final Path fichier;
    private final ObjectMapper mapper;

    public RepertoirePatients(@Value("${sunu.stockage.repertoire}") String repertoire, ObjectMapper mapper)
            throws IOException {
        this.mapper = mapper;
        Path dossier = Path.of(repertoire);
        Files.createDirectories(dossier);
        this.fichier = dossier.resolve("patients.json");
        if (Files.exists(fichier)) {
            List<Patient> charges = mapper.readValue(fichier.toFile(), new TypeReference<List<Patient>>() { });
            charges.forEach(p -> patients.put(p.id(), p));
        }
        log.info("{} patient(s) chargé(s)", patients.size());
    }

    public Optional<Patient> trouver(String id) {
        return Optional.ofNullable(patients.get(id));
    }

    public Collection<Patient> tous() {
        return patients.values();
    }

    public synchronized void enregistrer(Patient patient) {
        patients.put(patient.id(), patient);
        sauvegarder();
    }

    public synchronized boolean supprimer(String id) {
        boolean present = patients.remove(id) != null;
        if (present) {
            sauvegarder();
        }
        return present;
    }

    private void sauvegarder() {
        Path temporaire = fichier.resolveSibling("patients.json.tmp");
        try {
            mapper.writeValue(temporaire.toFile(), new ArrayList<>(patients.values()));
            Files.move(temporaire, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Sauvegarde des patients impossible", e);
        }
    }
}
