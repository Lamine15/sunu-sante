package sn.ugb.sunusante.patient.messagerie;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sunu.tampon")
public record ProprietesTampon(
        int capacite,
        Duration delaiInitial,
        Duration delaiMax,
        Duration delaiConfirmation) {
}
