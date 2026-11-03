package sn.ugb.sunusante.gateway.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@RestControllerAdvice
public class GestionErreurs {

    /** Disjoncteur ouvert : réponse immédiate, sans solliciter le service en difficulté. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, Object>> disjoncteurOuvert(CallNotPermittedException e) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("statut", 503);
        corps.put("erreur", "Service dossier médical momentanément protégé (disjoncteur ouvert). "
                + "Réessayez dans quelques secondes.");
        corps.put("disjoncteur", e.getCausingCircuitBreakerName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "15").body(corps);
    }

    /** Erreur renvoyée par un service : on relaie son statut et son corps. */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<String> erreurService(HttpStatusCodeException e) {
        return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(e.getResponseBodyAsString());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> injoignable(ResourceAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("statut", 503, "erreur", "Service injoignable : " + e.getMessage()));
    }
}
