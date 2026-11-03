package sn.ugb.sunusante.patient.modele;

import java.time.Instant;
import java.time.LocalDate;

public record Patient(
        String id,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        String telephone,
        String pathologie,
        String medecinTraitant,
        Instant inscritLe) {
}
