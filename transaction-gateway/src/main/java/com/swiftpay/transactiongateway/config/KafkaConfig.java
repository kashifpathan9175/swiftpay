package com.swiftpay.transactiongateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String PAYMENT_INITIATED_TOPIC =
            "swiftpay.payment.initiated";

    public static final String PAYMENT_RESULT_TOPIC =
            "swiftpay.payment.result";

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
    public NewTopic paymentResultTopic(
            @Value("${swiftpay.kafka.partitions:6}") int partitions,
            @Value("${swiftpay.kafka.replicas:1}") short replicas
    ) {
        return TopicBuilder
                .name(PAYMENT_RESULT_TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
