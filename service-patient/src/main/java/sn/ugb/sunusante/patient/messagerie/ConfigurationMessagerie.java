package sn.ugb.sunusante.patient.messagerie;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologie RabbitMQ du flux des mesures. Elle est déclarée à l'identique par le producteur
 * (ce service) et par le consommateur (service-analyse-temps-reel) : peu importe lequel
 * démarre en premier, la file existe avant la première publication.
 */
@Configuration
public class ConfigurationMessagerie {

    public static final String ECHANGE_MESURES = "sunu.mesures";
    public static final String FILE_MESURES = "mesures.analyse";
    public static final String CLE_MESURES = "mesure.capteur";
    public static final String ECHANGE_REJETS = "sunu.rejets";
    public static final String FILE_MESURES_REJETEES = "mesures.rejetees";

    @Bean
    public Declarables topologieMesures() {
        TopicExchange echange = new TopicExchange(ECHANGE_MESURES, true, false);
        DirectExchange rejets = new DirectExchange(ECHANGE_REJETS, true, false);
        Queue file = QueueBuilder.durable(FILE_MESURES)
                .deadLetterExchange(ECHANGE_REJETS)
                .deadLetterRoutingKey(FILE_MESURES_REJETEES)
                .build();
        Queue fileRejets = QueueBuilder.durable(FILE_MESURES_REJETEES).build();
        return new Declarables(echange, rejets, file, fileRejets,
                BindingBuilder.bind(file).to(echange).with("mesure.#"),
                BindingBuilder.bind(fileRejets).to(rejets).with(FILE_MESURES_REJETEES));
    }

    @Bean
    public MessageConverter convertisseurJson(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
