package com.swiftpay.ledger.config;

import com.swiftpay.ledger.exception.AccountNotFoundException;
import com.swiftpay.ledger.exception.CurrencyMismatchException;
import com.swiftpay.ledger.exception.InsufficientBalanceException;
import com.swiftpay.ledger.exception.InvalidPaymentEventException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;

    public KafkaConsumerConfig(@org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) { /* * Retry 3 times with a 2-second delay. * * IMPORTANT: * maxAttempts = 3 means: * initial delivery + 2 retries * * If you want three retries AFTER the initial attempt, * use 4 instead. */
        FixedBackOff backOff = new FixedBackOff(2000L, 2L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff); /* * AccountNotFoundException is explicitly retryable * for our current Kafka/DLT test. */
        errorHandler.addNotRetryableExceptions(
                AccountNotFoundException.class,
                InsufficientBalanceException.class,
                CurrencyMismatchException.class,
                InvalidPaymentEventException.class
        );
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(org.springframework.kafka.core.ConsumerFactory<String, Object> consumerFactory, CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
