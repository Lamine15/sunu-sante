package sn.ugb.sunusante.dossier.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import sn.ugb.sunusante.dossier.api.Requetes.NouveauDossier;
import sn.ugb.sunusante.dossier.api.Requetes.NouvelleMesure;
import sn.ugb.sunusante.dossier.api.Requetes.NouvelleNote;
import sn.ugb.sunusante.dossier.api.Requetes.NouvellePrescription;
import sn.ugb.sunusante.dossier.modele.DossierMedical;
import sn.ugb.sunusante.dossier.modele.EntreeMesure;
import sn.ugb.sunusante.dossier.modele.Note;
import sn.ugb.sunusante.dossier.modele.Prescription;
import sn.ugb.sunusante.dossier.quorum.CoordinateurQuorum;
import sn.ugb.sunusante.dossier.quorum.CoordinateurQuorum.ResultatQuorum;

/**
 * API publique, appelée de façon synchrone par l'API Gateway et le service d'analyse.
 * N'importe lequel des trois nœuds peut la servir : il devient coordinateur de la requête.
 */
@RestController
@RequestMapping("/dossiers")
@Validated
public class DossierController {

    private final CoordinateurQuorum coordinateur;

    public DossierController(CoordinateurQuorum coordinateur) {
        this.coordinateur = coordinateur;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReponseDossier creer(@Valid @RequestBody NouveauDossier requete) {
        ResultatQuorum resultat = coordinateur.modifier(requete.patientId(), actuel -> {
            if (actuel.isPresent()) {
                throw new DossierExistantException(requete.patientId());
            }
            return DossierMedical.nouveau(requete.patientId(), requete.nom(), requete.prenom(),
                    requete.dateNaissance(), requete.pathologie(), requete.medecinTraitant(), Instant.now());
        });
        return reponse(resultat);
    }

    @GetMapping("/{patientId}")
    public ReponseDossier lire(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId) {
        ResultatQuorum resultat = coordinateur.lire(patientId);
        if (resultat.dossier() == null) {
            throw new DossierIntrouvableException(patientId);
        }
        return reponse(resultat);
    }

    @PostMapping("/{patientId}/mesures")
    public ReponseDossier ajouterMesure(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId,
                                        @Valid @RequestBody NouvelleMesure requete) {
        EntreeMesure mesure = new EntreeMesure(
                requete.id() != null ? requete.id() : UUID.randomUUID().toString(),
                requete.type(), requete.valeur(), requete.unite(),
                requete.horodatage() != null ? requete.horodatage() : Instant.now(),
                requete.anomalie(), requete.commentaire());
        return reponse(coordinateur.modifier(patientId,
                actuel -> existant(actuel, patientId).avecMesure(mesure, Instant.now())));
    }

    @PostMapping("/{patientId}/prescriptions")
    public ReponseDossier ajouterPrescription(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId,
                                              @Valid @RequestBody NouvellePrescription requete) {
        Prescription prescription = new Prescription(UUID.randomUUID().toString(), requete.medicament(),
                requete.posologie(), requete.prescripteur(), Instant.now());
        return reponse(coordinateur.modifier(patientId,
                actuel -> existant(actuel, patientId).avecPrescription(prescription, Instant.now())));
    }

    @PostMapping("/{patientId}/notes")
    public ReponseDossier ajouterNote(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId,
                                      @Valid @RequestBody NouvelleNote requete) {
        Note note = new Note(UUID.randomUUID().toString(), requete.auteur(), requete.contenu(), Instant.now());
        return reponse(coordinateur.modifier(patientId,
                actuel -> existant(actuel, patientId).avecNote(note, Instant.now())));
    }

    /** Diagnostic : version détenue par chacune des répliques, lue sans quorum. */
    @GetMapping("/{patientId}/repliques")
    public Map<String, String> repliques(@PathVariable @Pattern(regexp = Requetes.MOTIF_ID) String patientId) {
        return coordinateur.etatRepliques(patientId);
    }

    private static DossierMedical existant(java.util.Optional<DossierMedical> actuel, String patientId) {
        return actuel.orElseThrow(() -> new DossierIntrouvableException(patientId));
    }

    private ReponseDossier reponse(ResultatQuorum resultat) {
        return ReponseDossier.depuis(resultat, coordinateur.noeudId(), coordinateur.description());
    }
}
