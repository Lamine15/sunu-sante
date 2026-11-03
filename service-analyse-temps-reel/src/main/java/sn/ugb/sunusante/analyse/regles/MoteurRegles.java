package sn.ugb.sunusante.analyse.regles;

import java.util.Optional;

import org.springframework.stereotype.Component;

import sn.ugb.sunusante.analyse.modele.Mesure;
import sn.ugb.sunusante.analyse.regles.ProprietesRegles.Seuil;

/** Règles de détection simples : une mesure hors de l'intervalle [min, max] de son type est une anomalie. */
@Component
public class MoteurRegles {

    public record Anomalie(String regle, String message) {
    }

    private final ProprietesRegles proprietes;

    public MoteurRegles(ProprietesRegles proprietes) {
        this.proprietes = proprietes;
    }

    public Optional<Anomalie> evaluer(Mesure mesure) {
        Seuil seuil = proprietes.regles().get(mesure.type());
        if (seuil == null) {
            return Optional.empty();
        }
        String libelle = libelle(mesure.type());
        if (mesure.valeur() > seuil.max()) {
            return Optional.of(new Anomalie("> " + seuil.max() + " " + mesure.unite(),
                    libelle + " trop élevé(e) : " + mesure.valeur() + " " + mesure.unite()
                            + " (maximum " + seuil.max() + ")"));
        }
        if (mesure.valeur() < seuil.min()) {
            return Optional.of(new Anomalie("< " + seuil.min() + " " + mesure.unite(),
                    libelle + " trop bas(se) : " + mesure.valeur() + " " + mesure.unite()
                            + " (minimum " + seuil.min() + ")"));
        }
        return Optional.empty();
    }

    private static String libelle(String type) {
        return switch (type) {
            case "RYTHME_CARDIAQUE" -> "Rythme cardiaque";
            case "GLUCOSE" -> "Glycémie";
            case "TEMPERATURE" -> "Température";
            case "TENSION_SYSTOLIQUE" -> "Tension systolique";
            case "SATURATION_OXYGENE" -> "Saturation en oxygène";
            default -> type;
        };
    }
}
