package sn.ugb.sunusante.patient.messagerie;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import sn.ugb.sunusante.patient.modele.Mesure;

/**
 * Publie les mesures dans RabbitMQ en tolérant l'indisponibilité du broker.
 *
 * <p>Une mesure n'est considérée comme publiée qu'après la confirmation du broker
 * (publisher confirms). En cas d'échec, elle est placée dans un tampon en mémoire, et le
 * tampon est vidé plus tard selon un backoff exponentiel avec gigue : 1 s, 2 s, 4 s, 8 s...
 * plafonné à {@code delai-max}. Tant que le tampon n'est pas vide, les nouvelles mesures y
 * sont ajoutées à la suite, pour conserver l'ordre d'arrivée.</p>
 */
@Component
public class PublicateurMesures {

    private static final Logger log = LoggerFactory.getLogger(PublicateurMesures.class);

    public enum Statut { PUBLIEE, EN_TAMPON }

    private final RabbitTemplate rabbit;
    private final ProprietesTampon proprietes;
    private final Deque<Mesure> tampon = new ConcurrentLinkedDeque<>();
    /** Protège la tête du tampon : vidage et éviction en cas de dépassement de capacité. */
    private final ReentrantLock verrouTete = new ReentrantLock();

    private volatile int echecsConsecutifs;
    private volatile Instant prochaineTentative = Instant.EPOCH;

    private final Counter recues;
    private final Counter publieesDirectement;
    private final Counter reemises;
    private final Counter misesEnTampon;
    private final Counter perdues;
    private final Counter tentatives;

    public PublicateurMesures(RabbitTemplate rabbit, ProprietesTampon proprietes, MeterRegistry registre) {
        this.rabbit = rabbit;
        this.proprietes = proprietes;
        this.recues = Counter.builder("mesures.capteurs.recues")
                .description("Mesures reçues des capteurs").register(registre);
        this.publieesDirectement = Counter.builder("mesures.capteurs.publiees").tag("mode", "direct")
                .description("Mesures confirmées par le broker").register(registre);
        this.reemises = Counter.builder("mesures.capteurs.publiees").tag("mode", "tampon")
                .description("Mesures confirmées par le broker").register(registre);
        this.misesEnTampon = Counter.builder("mesures.mises.en.tampon")
                .description("Mesures placées dans le tampon local").register(registre);
        this.perdues = Counter.builder("mesures.perdues")
                .description("Mesures écartées car le tampon était plein").register(registre);
        this.tentatives = Counter.builder("tampon.tentatives.reemission")
                .description("Tentatives de vidage du tampon").register(registre);
        Gauge.builder("mesures.en.tampon", tampon, Deque::size)
                .description("Mesures en attente de publication").register(registre);
    }

    public Statut publier(Mesure mesure) {
        recues.increment();
        if (!tampon.isEmpty()) {
            mettreEnTampon(mesure);
            return Statut.EN_TAMPON;
        }
        try {
            envoyer(mesure);
            publieesDirectement.increment();
            log.info("Mesure publiée : patient {}, {} = {} {}", mesure.patientId(), mesure.type(),
                    mesure.valeur(), mesure.unite());
            return Statut.PUBLIEE;
        } catch (RuntimeException e) {
            mettreEnTampon(mesure);
            planifierNouvelleTentative(e);
            return Statut.EN_TAMPON;
        }
    }

    /** Vérifie régulièrement s'il est temps de retenter le vidage du tampon. */
    @Scheduled(fixedDelay = 500)
    public void vider() {
        if (tampon.isEmpty() || Instant.now().isBefore(prochaineTentative)) {
            return;
        }
        tentatives.increment();
        int envoyees = 0;
        verrouTete.lock();
        try {
            Mesure mesure;
            while ((mesure = tampon.peekFirst()) != null) {
                try {
                    envoyer(mesure);
                } catch (RuntimeException e) {
                    if (envoyees > 0) {
                        log.info("{} mesure(s) réémise(s) avant une nouvelle coupure", envoyees);
                    }
                    planifierNouvelleTentative(e);
                    return;
                }
                tampon.pollFirst();
                reemises.increment();
                envoyees++;
            }
        } finally {
            verrouTete.unlock();
        }
        log.info("Broker de nouveau joignable : tampon vidé, {} mesure(s) réémise(s) après {} échec(s)",
                envoyees, echecsConsecutifs);
        echecsConsecutifs = 0;
        prochaineTentative = Instant.EPOCH;
    }

    public Map<String, Object> etat() {
        return Map.of(
                "mesuresEnTampon", tampon.size(),
                "capacite", proprietes.capacite(),
                "echecsConsecutifs", echecsConsecutifs,
                "prochaineTentative", tampon.isEmpty() ? "aucune" : prochaineTentative.toString());
    }

    private void envoyer(Mesure mesure) {
        rabbit.invoke(operations -> {
            operations.convertAndSend(ConfigurationMessagerie.ECHANGE_MESURES,
                    ConfigurationMessagerie.CLE_MESURES, mesure);
            operations.waitForConfirmsOrDie(proprietes.delaiConfirmation().toMillis());
            return Boolean.TRUE;
        });
    }

    private void mettreEnTampon(Mesure mesure) {
        tampon.addLast(mesure);
        misesEnTampon.increment();
        if (tampon.size() > proprietes.capacite()) {
            verrouTete.lock();
            try {
                Mesure ecartee = tampon.pollFirst();
                perdues.increment();
                log.error("Tampon plein ({} mesures) : mesure la plus ancienne écartée ({})",
                        proprietes.capacite(), ecartee != null ? ecartee.id() : "?");
            } finally {
                verrouTete.unlock();
            }
        }
        log.warn("Mesure mise en tampon : patient {}, {} = {} ({} en attente)",
                mesure.patientId(), mesure.type(), mesure.valeur(), tampon.size());
    }

    /** Backoff exponentiel : delai = min(initial x 2^(echecs-1), max), plus une gigue de 0 à 20 %. */
    private synchronized void planifierNouvelleTentative(Exception cause) {
        echecsConsecutifs++;
        long delai = delaiBackoff(echecsConsecutifs, proprietes.delaiInitial().toMillis(),
                proprietes.delaiMax().toMillis());
        long gigue = ThreadLocalRandom.current().nextLong(delai / 5 + 1);
        prochaineTentative = Instant.now().plusMillis(delai + gigue);
        log.warn("Broker injoignable ({}) : échec n°{}, nouvelle tentative dans {} ms, {} mesure(s) en tampon",
                cause.getMessage(), echecsConsecutifs, delai + gigue, tampon.size());
    }

    static long delaiBackoff(int echecs, long initialMs, long maxMs) {
        int exposant = Math.min(Math.max(echecs - 1, 0), 30);
        return Math.min(initialMs << exposant, maxMs);
    }
}
