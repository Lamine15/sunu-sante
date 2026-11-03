package sn.ugb.sunusante.analyse.messagerie;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ce service consomme le flux des mesures et produit le flux des alertes.
 * Les deux topologies sont déclarées à l'identique par leurs autres participants.
 */
@Configuration
public class ConfigurationMessagerie {

    public static final String ECHANGE_MESURES = "sunu.mesures";
    public static final String FILE_MESURES = "mesures.analyse";
    public static final String ECHANGE_ALERTES = "sunu.alertes";
    public static final String FILE_ALERTES = "alertes.notification";
    public static final String CLE_ALERTE_CRITIQUE = "alerte.critique";
    public static final String ECHANGE_REJETS = "sunu.rejets";
    public static final String FILE_MESURES_REJETEES = "mesures.rejetees";
    public static final String FILE_ALERTES_REJETEES = "alertes.rejetees";

    @Bean
    public Declarables topologie() {
        DirectExchange rejets = new DirectExchange(ECHANGE_REJETS, true, false);

        TopicExchange mesures = new TopicExchange(ECHANGE_MESURES, true, false);
        Queue fileMesures = QueueBuilder.durable(FILE_MESURES)
                .deadLetterExchange(ECHANGE_REJETS).deadLetterRoutingKey(FILE_MESURES_REJETEES).build();
        Queue mesuresRejetees = QueueBuilder.durable(FILE_MESURES_REJETEES).build();

        TopicExchange alertes = new TopicExchange(ECHANGE_ALERTES, true, false);
        Queue fileAlertes = QueueBuilder.durable(FILE_ALERTES)
                .deadLetterExchange(ECHANGE_REJETS).deadLetterRoutingKey(FILE_ALERTES_REJETEES).build();
        Queue alertesRejetees = QueueBuilder.durable(FILE_ALERTES_REJETEES).build();

        return new Declarables(rejets, mesures, fileMesures, mesuresRejetees, alertes, fileAlertes, alertesRejetees,
                BindingBuilder.bind(fileMesures).to(mesures).with("mesure.#"),
                BindingBuilder.bind(mesuresRejetees).to(rejets).with(FILE_MESURES_REJETEES),
                BindingBuilder.bind(fileAlertes).to(alertes).with("alerte.#"),
                BindingBuilder.bind(alertesRejetees).to(rejets).with(FILE_ALERTES_REJETEES));
    }

    @Bean
    public MessageConverter convertisseurJson(ObjectMapper mapper) {
        Jackson2JsonMessageConverter convertisseur = new Jackson2JsonMessageConverter(mapper);
        // Le type Java du producteur n'existe pas ici : on désérialise vers le type du paramètre de l'écouteur.
        convertisseur.setAlwaysConvertToInferredType(true);
        return convertisseur;
    }
}
