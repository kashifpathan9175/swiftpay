package com.swiftpay.transactiongateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String PAYMENT_INITIATED_TOPIC =
            "swiftpay.payment.initiated";

    public static final String PAYMENT_COMPLETED_TOPIC =
            "swiftpay.payment.completed";

    public static final String PAYMENT_FAILED_TOPIC =
            "swiftpay.payment.failed";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;


    // =========================================================
    // PRODUCER
    // =========================================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {

        Map<String, Object> properties = new HashMap<>();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
        );

        properties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );

        properties.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );

        properties.put(
                ProducerConfig.RETRIES_CONFIG,
                10
        );

        properties.put(
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                5
        );

        // Do not add Spring type headers.
        properties.put(
                JsonSerializer.ADD_TYPE_INFO_HEADERS,
                false
        );

        return new DefaultKafkaProducerFactory<>(properties);
    }


    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory
    ) {

        return new KafkaTemplate<>(producerFactory);
    }


    // =========================================================
    // TOPICS
    // =========================================================

    @Bean
    public NewTopic paymentInitiatedTopic(
            @Value("${swiftpay.kafka.partitions:6}") int partitions,
            @Value("${swiftpay.kafka.replicas:1}") short replicas
    ) {

        return TopicBuilder
                .name(PAYMENT_INITIATED_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }


    @Bean
    public NewTopic paymentCompletedTopic(
            @Value("${swiftpay.kafka.partitions:6}") int partitions,
            @Value("${swiftpay.kafka.replicas:1}") short replicas
    ) {

        return TopicBuilder
                .name(PAYMENT_COMPLETED_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }


    @Bean
    public NewTopic paymentFailedTopic(
            @Value("${swiftpay.kafka.partitions:6}") int partitions,
            @Value("${swiftpay.kafka.replicas:1}") short replicas
    ) {

        return TopicBuilder
                .name(PAYMENT_FAILED_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}