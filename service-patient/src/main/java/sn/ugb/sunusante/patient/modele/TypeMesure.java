package sn.ugb.sunusante.patient.modele;

public enum TypeMesure {
    RYTHME_CARDIAQUE("bpm"),
    GLUCOSE("g/L"),
    TEMPERATURE("°C"),
    TENSION_SYSTOLIQUE("mmHg"),
    SATURATION_OXYGENE("%");

    private final String unite;

    TypeMesure(String unite) {
        this.unite = unite;
    }

    public String unite() {
        return unite;
    }
}
