package com.swiftpay.ledger.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.domain.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.service.LedgerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentInitiatedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentInitiatedConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public PaymentInitiatedConsumer(
            LedgerService ledgerService,
            ObjectMapper objectMapper
    ) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "swiftpay.payment.initiated",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String payload) {

        try {
            String normalizedPayload = payload;

            if (normalizedPayload != null
                    && normalizedPayload.length() >= 2
                    && normalizedPayload.startsWith("\"")
                    && normalizedPayload.endsWith("\"")) {
                normalizedPayload = objectMapper.readValue(
                        normalizedPayload,
                        String.class
                );
            }

            PaymentInitiatedEvent event =
                    objectMapper.readValue(
                            normalizedPayload,
                            PaymentInitiatedEvent.class
                    );

            if (event == null) {
                throw new IllegalArgumentException(
                        "PaymentInitiatedEvent must not be null"
                );
            }

            log.info(
                    "Received PaymentInitiated event eventId={}, transactionId={}",
                    event.eventId(),
                    event.transactionId()
            );

            ledgerService.processPayment(event);

        } catch (Exception ex) {

            log.error(
                    "Failed to process PaymentInitiated event payload={}",
                    payload,
                    ex
            );

            throw new IllegalArgumentException(
                    "Invalid PaymentInitiatedEvent payload",
                    ex
            );
        }
    }
}