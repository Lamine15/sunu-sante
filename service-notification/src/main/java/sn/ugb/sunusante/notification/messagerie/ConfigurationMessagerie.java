package sn.ugb.sunusante.notification.messagerie;

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

/** Topologie du flux des alertes, déclarée à l'identique par service-analyse-temps-reel. */
@Configuration
public class ConfigurationMessagerie {

    public static final String ECHANGE_ALERTES = "sunu.alertes";
    public static final String FILE_ALERTES = "alertes.notification";
    public static final String ECHANGE_REJETS = "sunu.rejets";
    public static final String FILE_ALERTES_REJETEES = "alertes.rejetees";

    @Bean
    public Declarables topologieAlertes() {
        DirectExchange rejets = new DirectExchange(ECHANGE_REJETS, true, false);
        TopicExchange alertes = new TopicExchange(ECHANGE_ALERTES, true, false);
        Queue fileAlertes = QueueBuilder.durable(FILE_ALERTES)
                .deadLetterExchange(ECHANGE_REJETS).deadLetterRoutingKey(FILE_ALERTES_REJETEES).build();
        Queue alertesRejetees = QueueBuilder.durable(FILE_ALERTES_REJETEES).build();
        return new Declarables(rejets, alertes, fileAlertes, alertesRejetees,
                BindingBuilder.bind(fileAlertes).to(alertes).with("alerte.#"),
                BindingBuilder.bind(alertesRejetees).to(rejets).with(FILE_ALERTES_REJETEES));
    }

    @Bean
    public MessageConverter convertisseurJson(ObjectMapper mapper) {
        Jackson2JsonMessageConverter convertisseur = new Jackson2JsonMessageConverter(mapper);
        convertisseur.setAlwaysConvertToInferredType(true);
        return convertisseur;
    }
}
