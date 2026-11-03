package sn.ugb.sunusante.dossier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceDossierMedicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDossierMedicalApplication.class, args);
    }
}
