package sn.ugb.sunusante.dossier.api;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;
import sn.ugb.sunusante.dossier.modele.DossierVersionne;
import sn.ugb.sunusante.dossier.modele.ReponseReplique;
import sn.ugb.sunusante.dossier.stockage.StockageLocal;

/**
 * API de réplication, utilisée uniquement entre les nœuds du dossier médical.
 * Elle agit sur la seule réplique locale, sans quorum.
 */
@RestController
@RequestMapping("/interne/dossiers")
@Validated
public class InterneController {

    private final StockageLocal stockage;

    public InterneController(StockageLocal stockage) {
        this.stockage = stockage;
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<DossierVersionne> lire(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId) {
        return ResponseEntity.of(stockage.lire(patientId));
    }

    @PutMapping("/{patientId}")
    public ResponseEntity<ReponseReplique> ecrire(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId,
                                                  @RequestBody DossierVersionne dossier) {
        if (dossier.dossier() == null || !patientId.equals(dossier.dossier().patientId())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(stockage.appliquer(dossier));
    }
}
