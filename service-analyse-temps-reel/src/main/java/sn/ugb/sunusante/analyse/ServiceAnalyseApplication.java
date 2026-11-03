package sn.ugb.sunusante.analyse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceAnalyseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAnalyseApplication.class, args);
    }
}
