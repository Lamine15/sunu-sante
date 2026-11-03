package sn.ugb.sunusante.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import sn.ugb.sunusante.notification.messagerie.ConfigurationMessagerie;
import sn.ugb.sunusante.notification.modele.AlerteCritique;

/**
 * Abonné aux événements « Alerte Critique ». L'envoi au médecin traitant (SMS, e-mail,
 * notification push) est simulé par une écriture dans la console.
 *
 * <p>Le broker garantit une livraison « au moins une fois » : une même alerte peut arriver
 * deux fois. Les identifiants déjà traités sont mémorisés pour ne pas notifier deux fois.</p>
 */
@Component
@RestController
public class NotificateurAlertes {

    private static final Logger log = LoggerFactory.getLogger(NotificateurAlertes.class);
    private static final List<String> CANAUX = List.of("SMS", "EMAIL", "PUSH");
    private static final int MEMOIRE_IDS = 10_000;
    private static final int HISTORIQUE = 50;

    public record Notification(String alerteId, String patientId, String canal, String message, Instant envoyeeLe) {
    }

    private final Set<String> dejaTraitees = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> plusAncienne) {
            return size() > MEMOIRE_IDS;
        }
    });
    private final Deque<Notification> dernieres = new ConcurrentLinkedDeque<>();
    private final MeterRegistry registre;
    private final Counter doublons;

    public NotificateurAlertes(MeterRegistry registre) {
        this.registre = registre;
        this.doublons = Counter.builder("notifications.doublons.ignores")
                .description("Alertes reçues en double et non renotifiées").register(registre);
    }

    @RabbitListener(queues = ConfigurationMessagerie.FILE_ALERTES)
    public void notifier(AlerteCritique alerte) {
        synchronized (dejaTraitees) {
            if (!dejaTraitees.add(alerte.id())) {
                doublons.increment();
                log.info("Alerte {} déjà notifiée, doublon ignoré", alerte.id());
                return;
            }
        }
        String message = "ALERTE CRITIQUE - patient " + alerte.patientId() + " : " + alerte.message()
                + " (mesure du " + alerte.horodatageMesure() + ")";
        for (String canal : CANAUX) {
            log.warn(">>> [{}] au médecin traitant du patient {} : {}", canal, alerte.patientId(), message);
            Counter.builder("notifications.envoyees").tag("canal", canal).register(registre).increment();
            dernieres.addFirst(new Notification(alerte.id(), alerte.patientId(), canal, message, Instant.now()));
        }
        while (dernieres.size() > HISTORIQUE) {
            dernieres.pollLast();
        }
    }

    @GetMapping("/notifications")
    public List<Notification> dernieresNotifications() {
        return new ArrayList<>(dernieres);
    }
}
