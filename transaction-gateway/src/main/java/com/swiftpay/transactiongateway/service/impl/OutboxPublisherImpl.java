package com.swiftpay.transactiongateway.service.impl;

import com.swiftpay.transactiongateway.domain.entities.OutboxEvent;
import com.swiftpay.transactiongateway.domain.enums.OutboxStatus;
import com.swiftpay.transactiongateway.repository.OutboxEventRepository;
import com.swiftpay.transactiongateway.service.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisherImpl implements OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherImpl.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration KAFKA_SEND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;


    public OutboxPublisherImpl(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(
            fixedDelayString = "${swiftpay.outbox.poll-delay-ms:1000}"
    )
    @Transactional
    @Override
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxEventRepository.findAvailableEvents(
                        OutboxStatus.PENDING,
                        Instant.now(),
                        PageRequest.of(0, BATCH_SIZE)
                );

        if (events.isEmpty()) {
            return;
        }

        log.debug(
                "Found {} pending outbox events",
                events.size()
        );

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            event.markAttempt();
            outboxEventRepository.saveAndFlush(event);

            SendResult<String, Object> result = kafkaTemplate
                    .send(
                            event.getTopic(),
                            event.getAggregateId(),
                            event.getPayload()
                    )
                    .get(KAFKA_SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            if (result == null) {
                throw new IllegalStateException(
                        "Kafka send completed without a result for eventId=" + event.getId()
                );
            }

            event.markPublished(Instant.now());
            outboxEventRepository.saveAndFlush(event);

            log.info(
                    "Published outbox event eventId={}, aggregateId={}, topic={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getTopic()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handleFailedPublication(event, ex);
        } catch (ExecutionException | TimeoutException ex) {
            handleFailedPublication(event, ex);
        } catch (Exception ex) {
            handleFailedPublication(event, ex);
        }
    }

    private void handleFailedPublication(OutboxEvent event, Exception ex) {
        log.error(
                "Failed to publish outbox event eventId={}, aggregateId={}, topic={}, attempt={}",
                event.getId(),
                event.getAggregateId(),
                event.getTopic(),
                event.getAttempts(),
                ex
        );

        event.reschedule(Instant.now().plus(RETRY_BACKOFF));
        outboxEventRepository.saveAndFlush(event);
    }
}
