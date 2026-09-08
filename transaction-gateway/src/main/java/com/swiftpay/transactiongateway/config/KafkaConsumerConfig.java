package com.swiftpay.transactiongateway.config;

import com.swiftpay.transactiongateway.domain.events.PaymentCompletedEvent;
import com.swiftpay.transactiongateway.domain.events.PaymentFailedEvent;
import com.swiftpay.transactiongateway.exception.InvalidPaymentException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 2000L;
    private static final long MAX_ATTEMPTS = 3L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private Map<String, Object> baseConsumerProperties() {

        Map<String, Object> props = new HashMap<>();

        props.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        props.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                groupId
        );

        props.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        props.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        return props;
    }

    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent>
    paymentCompletedConsumerFactory() {

        JsonDeserializer<PaymentCompletedEvent> deserializer =
                new JsonDeserializer<>(PaymentCompletedEvent.class);

        deserializer.addTrustedPackages(
                "com.swiftpay.transactiongateway.domain.events"
        );

        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent>
    paymentFailedConsumerFactory() {

        JsonDeserializer<PaymentFailedEvent> deserializer =
                new JsonDeserializer<>(PaymentFailedEvent.class);

        deserializer.addTrustedPackages(
                "com.swiftpay.transactiongateway.domain.events"
        );

        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                )
        );
    }

    @Bean
    public CommonErrorHandler paymentResultErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer
    ) {
        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                deadLetterPublishingRecoverer,
                backOff
        );

        errorHandler.addNotRetryableExceptions(
                InvalidPaymentException.class,
                IllegalArgumentException.class,
                IllegalStateException.class
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent>
    paymentCompletedKafkaListenerContainerFactory(
            CommonErrorHandler paymentResultErrorHandler
    ) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                paymentCompletedConsumerFactory()
        );

        factory.setCommonErrorHandler(paymentResultErrorHandler);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent>
    paymentFailedKafkaListenerContainerFactory(
            CommonErrorHandler paymentResultErrorHandler
    ) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                paymentFailedConsumerFactory()
        );

        factory.setCommonErrorHandler(paymentResultErrorHandler);

        return factory;
    }

    @Bean
    public NewTopic paymentCompletedDltTopic() {
        return TopicBuilder
                .name("swiftpay.payment.completed.DLT")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedDltTopic() {
        return TopicBuilder
                .name("swiftpay.payment.failed.DLT")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
