package sn.ugb.sunusante.dossier.api;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simulation d'une surcharge du nœud, pour démontrer le disjoncteur de l'API Gateway.
 * Elle ralentit (et peut faire échouer) l'API publique /dossiers ; l'API interne de
 * réplication n'est pas touchée.
 */
@Component
public class SimulationSurcharge extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SimulationSurcharge.class);

    public record Reglage(boolean active, long latenceMs, double tauxErreur) {
    }

    private volatile Reglage reglage = new Reglage(false, 0, 0);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !requete.getRequestURI().startsWith("/dossiers");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        Reglage courant = reglage;
        if (courant.active()) {
            if (courant.latenceMs() > 0) {
                try {
                    Thread.sleep(courant.latenceMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (ThreadLocalRandom.current().nextDouble() < courant.tauxErreur()) {
                reponse.setStatus(503);
                reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                reponse.setCharacterEncoding("UTF-8");
                reponse.getWriter().write("{\"statut\":503,\"erreur\":\"Nœud surchargé (simulation)\"}");
                return;
            }
        }
        chaine.doFilter(requete, reponse);
    }

    @RestController
    public static class AdminSurcharge {

        private final SimulationSurcharge simulation;

        public AdminSurcharge(SimulationSurcharge simulation) {
            this.simulation = simulation;
        }

        @GetMapping("/admin/surcharge")
        public Reglage lire() {
            return simulation.reglage;
        }

        @PostMapping("/admin/surcharge")
        public Map<String, Object> regler(@RequestBody Reglage nouveau) {
            simulation.reglage = nouveau;
            log.warn("Simulation de surcharge {} : latence {} ms, taux d'erreur {}",
                    nouveau.active() ? "ACTIVÉE" : "désactivée", nouveau.latenceMs(), nouveau.tauxErreur());
            return Map.of("surcharge", nouveau);
        }
    }
}
