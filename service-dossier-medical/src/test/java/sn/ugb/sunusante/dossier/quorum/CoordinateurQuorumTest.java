package sn.ugb.sunusante.dossier.quorum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import sn.ugb.sunusante.dossier.modele.DossierMedical;
import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.Note;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;

class CoordinateurQuorumTest {

    /** Réplique en mémoire que l'on peut mettre en panne. */
    static class RepliqueFactice implements Replique {
        final String nom;
        final Map<String, DossierVersionne> donnees = new ConcurrentHashMap<>();
        volatile boolean enPanne;

        RepliqueFactice(String nom) {
            this.nom = nom;
        }

        @Override
        public String nom() {
            return nom;
        }

        @Override
        public Optional<DossierVersionne> lire(String patientId) {
            if (enPanne) {
                throw new IllegalStateException("connexion refusée");
            }
            return Optional.ofNullable(donnees.get(patientId));
        }

        @Override
        public synchronized ReponseReplique ecrire(DossierVersionne dossier) {
            if (enPanne) {
                throw new IllegalStateException("connexion refusée");
            }
            DossierVersionne actuel = donnees.get(dossier.dossier().patientId());
            if (dossier.memeVersionQue(actuel) || dossier.estPlusRecenteQue(actuel)) {
                donnees.put(dossier.dossier().patientId(), dossier);
                return new ReponseReplique(nom, true, dossier.version(), dossier.noeudAuteur());
            }
            return new ReponseReplique(nom, false, actuel.version(), actuel.noeudAuteur());
        }
    }

    private ExecutorService executeur;
    private RepliqueFactice a;
    private RepliqueFactice b;
    private RepliqueFactice c;
    private CoordinateurQuorum coordinateur;

    @BeforeEach
    void preparer() {
        executeur = Executors.newFixedThreadPool(8);
        a = new RepliqueFactice("a");
        b = new RepliqueFactice("b");
        c = new RepliqueFactice("c");
        coordinateur = new CoordinateurQuorum("a", List.of(a, b, c), 2, 2, Duration.ofMillis(300),
                executeur, new SimpleMeterRegistry());
    }

    @AfterEach
    void arreter() {
        executeur.shutdownNow();
    }

    private static DossierMedical dossier(String id) {
        return DossierMedical.nouveau(id, "Ndiaye", "Awa", null, "Diabète", "Dr Fall", Instant.now());
    }

    @Test
    void ecritureReussieAvecUneRepliqueEnPanne() {
        c.enPanne = true;

        var resultat = coordinateur.modifier("P1", actuel -> dossier("P1"));

        assertThat(resultat.dossier().version()).isEqualTo(1);
        assertThat(resultat.repliques()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void ecritureRefuseeSansQuorum() {
        b.enPanne = true;
        c.enPanne = true;

        assertThatThrownBy(() -> coordinateur.modifier("P1", actuel -> dossier("P1")))
                .isInstanceOf(QuorumNonAtteintException.class);
    }

    @Test
    void lectureRenvoieLaVersionLaPlusRecenteEtRepareLesRepliquesEnRetard() throws Exception {
        coordinateur.modifier("P1", actuel -> dossier("P1"));
        Thread.sleep(100);

        // c tombe, une note est ajoutée : seules a et b ont la v2.
        c.enPanne = true;
        coordinateur.modifier("P1", actuel -> actuel.orElseThrow()
                .avecNote(new Note("n1", "Dr Fall", "Contrôle dans 1 mois", Instant.now()), Instant.now()));
        c.enPanne = false;
        assertThat(c.donnees.get("P1").version()).isEqualTo(1);

        // a tombe : la lecture interroge b (v2) et c (v1) et doit renvoyer la v2.
        a.enPanne = true;
        var lecture = coordinateur.lire("P1");

        assertThat(lecture.dossier().version()).isEqualTo(2);
        assertThat(lecture.dossier().dossier().notes()).hasSize(1);
        Thread.sleep(100);
        assertThat(c.donnees.get("P1").version()).as("réparation à la lecture").isEqualTo(2);
    }

    @Test
    void lectureRefuseeSansQuorum() {
        a.enPanne = true;
        b.enPanne = true;

        assertThatThrownBy(() -> coordinateur.lire("P1")).isInstanceOf(QuorumNonAtteintException.class);
    }

    @Test
    void wPlusRSuperieurANEstExigeCoherent() {
        assertThatThrownBy(() -> new CoordinateurQuorum("a", List.of(a, b, c), 4, 2, Duration.ofMillis(300),
                executeur, new SimpleMeterRegistry())).isInstanceOf(IllegalArgumentException.class);
    }
}
