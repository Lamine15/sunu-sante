package sn.ugb.sunusante.dossier.quorum;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import sn.ugb.sunusante.dossier.modele.DossierMedical;
import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;

/**
 * Coordinateur de quorum, sans leader : chaque nœud peut coordonner une requête.
 *
 * <ul>
 *   <li>Lecture : interroger les N répliques en parallèle, attendre R réponses,
 *       renvoyer la version la plus récente et réparer les répliques en retard.</li>
 *   <li>Écriture : lire au quorum, appliquer la modification, numéroter la nouvelle
 *       version (max + 1), l'envoyer aux N répliques et attendre W acquittements.</li>
 * </ul>
 *
 * Avec W + R &gt; N, l'ensemble lu et l'ensemble écrit ont au moins une réplique en commun :
 * toute lecture réussie voit la dernière écriture réussie.
 */
public class CoordinateurQuorum {

    private static final Logger log = LoggerFactory.getLogger(CoordinateurQuorum.class);

    private final String noeudId;
    private final List<Replique> repliques;
    private final int n;
    private final int w;
    private final int r;
    private final Duration attenteMax;
    private final ExecutorService executeur;
    private final Map<String, Object> verrous = new ConcurrentHashMap<>();

    private final Counter ecrituresReussies;
    private final Counter ecrituresEchouees;
    private final Counter conflits;
    private final Counter lecturesReussies;
    private final Counter lecturesEchouees;
    private final Counter reparations;
    private final Timer dureeLecture;
    private final Timer dureeEcriture;

    public CoordinateurQuorum(String noeudId, List<Replique> repliques, int w, int r, Duration delaiReplique,
                              ExecutorService executeur, MeterRegistry registre) {
        this.noeudId = noeudId;
        this.repliques = List.copyOf(repliques);
        this.n = repliques.size();
        this.w = w;
        this.r = r;
        // Marge au-delà du délai d'une réplique, pour laisser revenir une réponse tardive mais valide.
        this.attenteMax = delaiReplique.plusMillis(500);
        this.executeur = executeur;

        if (w < 1 || r < 1 || w > n || r > n) {
            throw new IllegalArgumentException("Quorum invalide : N=" + n + ", W=" + w + ", R=" + r
                    + " (il faut 1 <= W <= N et 1 <= R <= N ; vérifiez NOEUD_PAIRS)");
        }
        if (w + r > n) {
            log.info("Quorum N={}, W={}, R={} : W + R > N, toute lecture recoupe la dernière écriture réussie", n, w, r);
        } else {
            log.warn("Quorum N={}, W={}, R={} : W + R <= N, une lecture peut ne pas voir la dernière écriture", n, w, r);
        }

        this.ecrituresReussies = Counter.builder("dossiers.medicaux.mis.a.jour")
                .description("Écritures de dossiers confirmées par au moins W répliques").register(registre);
        this.ecrituresEchouees = Counter.builder("quorum.ecritures.echouees")
                .description("Écritures n'ayant pas atteint W acquittements").register(registre);
        this.conflits = Counter.builder("quorum.ecritures.conflits")
                .description("Écritures rejetées car une version concurrente plus récente existait").register(registre);
        this.lecturesReussies = Counter.builder("quorum.lectures").tag("resultat", "succes").register(registre);
        this.lecturesEchouees = Counter.builder("quorum.lectures").tag("resultat", "echec").register(registre);
        this.reparations = Counter.builder("quorum.reparations.lecture")
                .description("Répliques en retard remises à jour lors d'une lecture (read repair)").register(registre);
        this.dureeLecture = Timer.builder("quorum.lecture.duree").register(registre);
        this.dureeEcriture = Timer.builder("quorum.ecriture.duree").register(registre);
    }

    /** Lecture au quorum R. Le dossier du résultat est nul si aucune réplique ne le connaît. */
    public ResultatQuorum lire(String patientId) {
        return dureeLecture.record(() -> {
            ResultatQuorum resultat = lireQuorum(patientId);
            lecturesReussies.increment();
            return resultat;
        });
    }

    /**
     * Lecture-modification-écriture au quorum. La fonction reçoit la version courante du dossier
     * (vide s'il n'existe pas) et renvoie la nouvelle ; si elle renvoie le même objet, rien n'est écrit.
     */
    public ResultatQuorum modifier(String patientId, Function<Optional<DossierMedical>, DossierMedical> modification) {
        // Sérialise les modifications d'un même dossier coordonnées par ce nœud.
        synchronized (verrous.computeIfAbsent(patientId, k -> new Object())) {
            return dureeEcriture.record(() -> {
                ResultatQuorum lecture = lireQuorum(patientId);
                Optional<DossierVersionne> actuelle = Optional.ofNullable(lecture.dossier());
                DossierMedical nouveau = modification.apply(actuelle.map(DossierVersionne::dossier));
                if (actuelle.isPresent() && nouveau == actuelle.get().dossier()) {
                    return lecture;
                }
                long version = actuelle.map(DossierVersionne::version).orElse(0L) + 1;
                DossierVersionne versionNouvelle = new DossierVersionne(nouveau, version, noeudId, Instant.now());
                List<String> acquittements = ecrireQuorum(versionNouvelle);
                ecrituresReussies.increment();
                return new ResultatQuorum(versionNouvelle, acquittements);
            });
        }
    }

    /** Version détenue par chaque réplique, sans quorum : sert au diagnostic et aux démonstrations. */
    public Map<String, String> etatRepliques(String patientId) {
        Map<String, String> etat = new LinkedHashMap<>();
        for (Replique replique : repliques) {
            try {
                etat.put(replique.nom(), replique.lire(patientId)
                        .map(v -> "v" + v.version() + " (écrite par " + v.noeudAuteur() + ")")
                        .orElse("absent"));
            } catch (Exception e) {
                etat.put(replique.nom(), "injoignable : " + message(e));
            }
        }
        return etat;
    }

    public String noeudId() {
        return noeudId;
    }

    public String description() {
        return "N=" + n + ", W=" + w + ", R=" + r;
    }

    private ResultatQuorum lireQuorum(String patientId) {
        CompletionService<ReponseLecture> service = new ExecutorCompletionService<>(executeur);
        for (Replique replique : repliques) {
            service.submit(() -> {
                try {
                    return new ReponseLecture(replique, replique.lire(patientId), null);
                } catch (Exception e) {
                    return new ReponseLecture(replique, Optional.empty(), message(e));
                }
            });
        }
        List<ReponseLecture> recues = attendre(service, r, ReponseLecture::reussie);
        List<ReponseLecture> reussies = recues.stream().filter(ReponseLecture::reussie).toList();
        if (reussies.size() < r) {
            lecturesEchouees.increment();
            List<String> echecs = echecs(recues.stream().filter(x -> !x.reussie())
                    .map(x -> x.replique().nom() + " : " + x.erreur()).toList(),
                    recues.stream().map(ReponseLecture::replique).toList());
            log.error("Lecture du dossier {} : quorum R={} non atteint ({})", patientId, r, echecs);
            throw new QuorumNonAtteintException("lecture", reussies.size(), r, echecs);
        }

        DossierVersionne plusRecente = null;
        for (ReponseLecture reponse : reussies) {
            DossierVersionne v = reponse.valeur().orElse(null);
            if (v != null && v.estPlusRecenteQue(plusRecente)) {
                plusRecente = v;
            }
        }
        if (plusRecente != null) {
            reparer(plusRecente, reussies);
        }
        return new ResultatQuorum(plusRecente, reussies.stream().map(x -> x.replique().nom()).toList());
    }

    /** Read repair : les répliques consultées qui détiennent une version périmée reçoivent la plus récente. */
    private void reparer(DossierVersionne plusRecente, List<ReponseLecture> reponses) {
        for (ReponseLecture reponse : reponses) {
            DossierVersionne detenue = reponse.valeur().orElse(null);
            if (plusRecente.estPlusRecenteQue(detenue)) {
                reparations.increment();
                log.info("Réparation à la lecture : {} détient {} du dossier {}, envoi de v{}",
                        reponse.replique().nom(), detenue == null ? "aucune version" : "v" + detenue.version(),
                        plusRecente.dossier().patientId(), plusRecente.version());
                executeur.submit(() -> {
                    try {
                        reponse.replique().ecrire(plusRecente);
                    } catch (Exception e) {
                        log.warn("Réparation de {} impossible : {}", reponse.replique().nom(), message(e));
                    }
                });
            }
        }
    }

    private List<String> ecrireQuorum(DossierVersionne version) {
        String patientId = version.dossier().patientId();
        CompletionService<ReponseEcriture> service = new ExecutorCompletionService<>(executeur);
        for (Replique replique : repliques) {
            service.submit(() -> {
                try {
                    return new ReponseEcriture(replique, replique.ecrire(version), null);
                } catch (Exception e) {
                    return new ReponseEcriture(replique, null, message(e));
                }
            });
        }
        // Les écritures vers les répliques restantes se poursuivent en arrière-plan une fois W atteint.
        List<ReponseEcriture> recues = attendre(service, w, ReponseEcriture::acquittee);
        List<String> acquittements = recues.stream().filter(ReponseEcriture::acquittee)
                .map(x -> x.replique().nom()).toList();

        if (acquittements.size() < w) {
            ecrituresEchouees.increment();
            List<String> echecs = echecs(recues.stream().filter(x -> !x.acquittee())
                    .map(x -> x.replique().nom() + " : " + (x.erreur() != null ? x.erreur()
                            : "détient déjà v" + x.reponse().versionStockee() + " de " + x.reponse().auteurStocke()))
                    .toList(), recues.stream().map(ReponseEcriture::replique).toList());
            log.error("Écriture du dossier {} v{} : quorum W={} non atteint, {} acquittement(s) ({})",
                    patientId, version.version(), w, acquittements.size(), echecs);
            boolean conflit = recues.stream().anyMatch(x -> x.reponse() != null && !x.reponse().acceptee());
            if (conflit) {
                conflits.increment();
                throw new ConflitEcritureException(patientId);
            }
            throw new QuorumNonAtteintException("écriture", acquittements.size(), w, echecs);
        }
        log.info("Dossier {} v{} : écriture confirmée par {} réplique(s) sur {} {} (W={})",
                patientId, version.version(), acquittements.size(), n, acquittements, w);
        return acquittements;
    }

    /** Recueille les réponses jusqu'à obtenir {@code requis} succès, épuiser les N répliques ou dépasser le délai. */
    private <T> List<T> attendre(CompletionService<T> service, int requis, Predicate<T> succes) {
        List<T> recues = new ArrayList<>();
        int reussites = 0;
        long limite = System.nanoTime() + attenteMax.toNanos();
        try {
            while (recues.size() < n && reussites < requis) {
                Future<T> future = service.poll(limite - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }
                T reponse = future.get();
                recues.add(reponse);
                if (succes.test(reponse)) {
                    reussites++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Impossible en pratique : chaque tâche capture ses propres erreurs.
            throw new IllegalStateException(e.getCause());
        }
        return recues;
    }

    /** Ajoute aux échecs déclarés les répliques qui n'ont pas répondu dans le délai. */
    private List<String> echecs(List<String> declares, List<Replique> ayantRepondu) {
        List<String> echecs = new ArrayList<>(declares);
        repliques.stream().filter(rep -> !ayantRepondu.contains(rep))
                .forEach(rep -> echecs.add(rep.nom() + " : pas de réponse en " + attenteMax.toMillis() + " ms"));
        return echecs;
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Résultat d'une opération de quorum : la version retenue et les répliques qui y ont participé. */
    public record ResultatQuorum(DossierVersionne dossier, List<String> repliques) {
    }

    private record ReponseLecture(Replique replique, Optional<DossierVersionne> valeur, String erreur) {
        boolean reussie() {
            return erreur == null;
        }
    }

    private record ReponseEcriture(Replique replique, ReponseReplique reponse, String erreur) {
        boolean acquittee() {
            return reponse != null && reponse.acceptee();
        }
    }
}
