package sn.ugb.sunusante.dossier.quorum;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.MeterRegistry;
import sn.ugb.sunusante.dossier.stockage.StockageLocal;

@Configuration
public class ConfigurationQuorum {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService executeurQuorum() {
        AtomicInteger compteur = new AtomicInteger();
        return Executors.newFixedThreadPool(32, tache -> {
            Thread fil = new Thread(tache, "quorum-" + compteur.incrementAndGet());
            fil.setDaemon(true);
            return fil;
        });
    }

    @Bean
    public CoordinateurQuorum coordinateurQuorum(ProprietesQuorum proprietes, StockageLocal stockage,
                                                 RestClient.Builder builder, ExecutorService executeurQuorum,
                                                 MeterRegistry registre) {
        List<Replique> repliques = new ArrayList<>();
        repliques.add(new RepliqueLocale(proprietes.noeudId() + " (local)", stockage));
        for (String pair : proprietes.pairs()) {
            repliques.add(new RepliqueDistante(pair, builder.clone(), proprietes.delaiReplique()));
        }
        return new CoordinateurQuorum(proprietes.noeudId(), repliques, proprietes.w(), proprietes.r(),
                proprietes.delaiReplique(), executeurQuorum, registre);
    }
}
