package sn.ugb.sunusante.dossier.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;
import sn.ugb.sunusante.dossier.quorum.ConflitEcritureException;
import sn.ugb.sunusante.dossier.quorum.QuorumNonAtteintException;

@RestControllerAdvice
public class GestionErreurs {

    @ExceptionHandler(QuorumNonAtteintException.class)
    public ResponseEntity<Map<String, Object>> quorum(QuorumNonAtteintException e) {
        return erreur(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e.getEchecs());
    }

    @ExceptionHandler(ConflitEcritureException.class)
    public ResponseEntity<Map<String, Object>> conflit(ConflitEcritureException e) {
        return erreur(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(DossierIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> introuvable(DossierIntrouvableException e) {
        return erreur(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(DossierExistantException.class)
    public ResponseEntity<Map<String, Object>> existant(DossierExistantException e) {
        return erreur(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<Map<String, Object>> invalide(Exception e) {
        return erreur(HttpStatus.BAD_REQUEST, "Requête invalide", null);
    }

    static ResponseEntity<Map<String, Object>> erreur(HttpStatus statut, String message, List<String> details) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("statut", statut.value());
        corps.put("erreur", message);
        if (details != null && !details.isEmpty()) {
            corps.put("details", details);
        }
        return ResponseEntity.status(statut).body(corps);
    }
}
