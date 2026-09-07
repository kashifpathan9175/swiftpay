package com.swiftpay.transactiongateway.service.impl;

import com.swiftpay.transactiongateway.domain.entities.OutboxEvent;
import com.swiftpay.transactiongateway.domain.enums.OutboxStatus;
import com.swiftpay.transactiongateway.repository.OutboxEventRepository;
import com.swiftpay.transactiongateway.service.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisherImpl implements OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherImpl.class);

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    public OutboxPublisherImpl(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate
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

    private void publish(
            OutboxEvent event
    ) {
        try {
            event.markAttempt();

            kafkaTemplate
                    .send(
                            event.getTopic(),
                            event.getAggregateId(),
                            event.getPayload()
                    )
                    .whenComplete((result, exception) -> {

                        if (exception != null) {

                            log.error(
                                    "Failed to publish outbox event eventId={}, transactionId={}, attempt={}",
                                    event.getId(),
                                    event.getAggregateId(),
                                    event.getAttempts(),
                                    exception
                            );

                            return;
                        }

                        log.info(
                                "Published outbox event eventId={}, transactionId={}, topic={}",
                                event.getId(),
                                event.getAggregateId(),
                                event.getTopic()
                        );

                        event.markPublished(
                                Instant.now()
                        );

                        outboxEventRepository.save(event);
                    });

        } catch (Exception ex) {

            log.error(
                    "Unexpected error publishing outbox event eventId={}",
                    event.getId(),
                    ex
            );
        }
    }
}
