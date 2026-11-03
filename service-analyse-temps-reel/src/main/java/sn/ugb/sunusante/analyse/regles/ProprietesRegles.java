package sn.ugb.sunusante.analyse.regles;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Seuils de normalité par type de mesure, lus depuis la configuration (sunu.regles). */
@ConfigurationProperties(prefix = "sunu")
public record ProprietesRegles(Map<String, Seuil> regles) {

    public record Seuil(double min, double max) {
    }

    public ProprietesRegles {
        regles = regles == null ? Map.of() : Map.copyOf(regles);
    }
}
