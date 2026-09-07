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

    public PaymentInitiatedConsumer(
            LedgerService ledgerService
    ) {
        this.ledgerService = ledgerService;
    }

    @KafkaListener(
            topics = "swiftpay.payment.initiated",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(PaymentInitiatedEvent event) {

        if (event == null) {
            log.error(
                    "Received null PaymentInitiatedEvent"
            );

            throw new IllegalArgumentException(
                    "PaymentInitiatedEvent must not be null"
            );
        }

        log.debug(
                "Received PaymentInitiated event eventId={}, transactionId={}",
                event.eventId(),
                event.transactionId()
        );

        ledgerService.processPayment(event);
    }
}
