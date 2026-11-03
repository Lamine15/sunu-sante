package sn.ugb.sunusante.analyse.regles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sn.ugb.sunusante.analyse.modele.Mesure;
import sn.ugb.sunusante.analyse.regles.ProprietesRegles.Seuil;

class MoteurReglesTest {

    private final MoteurRegles moteur = new MoteurRegles(new ProprietesRegles(Map.of(
            "RYTHME_CARDIAQUE", new Seuil(50, 100),
            "GLUCOSE", new Seuil(0.7, 1.8))));

    private static Mesure mesure(String type, double valeur, String unite) {
        return new Mesure("m", "PAT-1", type, valeur, unite, Instant.now());
    }

    @Test
    void rythmeCardiaqueSuperieurA100EstUneAnomalie() {
        assertThat(moteur.evaluer(mesure("RYTHME_CARDIAQUE", 135, "bpm"))).isPresent();
    }

    @Test
    void rythmeCardiaqueNormalNestPasUneAnomalie() {
        assertThat(moteur.evaluer(mesure("RYTHME_CARDIAQUE", 100, "bpm"))).isEmpty();
        assertThat(moteur.evaluer(mesure("RYTHME_CARDIAQUE", 72, "bpm"))).isEmpty();
    }

    @Test
    void hypoglycemieEstUneAnomalie() {
        assertThat(moteur.evaluer(mesure("GLUCOSE", 0.5, "g/L")))
                .hasValueSatisfying(a -> assertThat(a.regle()).startsWith("<"));
    }

    @Test
    void typeSansRegleNestPasEvalue() {
        assertThat(moteur.evaluer(mesure("POIDS", 300, "kg"))).isEmpty();
    }
}
