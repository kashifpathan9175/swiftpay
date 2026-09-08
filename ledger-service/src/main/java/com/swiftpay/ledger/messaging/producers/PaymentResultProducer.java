package com.swiftpay.ledger.messaging.producers;

import com.swiftpay.ledger.domain.event.PaymentCompletedEvent;
import com.swiftpay.ledger.domain.event.PaymentFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentResultProducer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCompleted(
            PaymentCompletedEvent event
    ) {
        kafkaTemplate.send(
                "swiftpay.payment.completed",
                event.transactionId(),
                event
        );
    }

    public void publishFailed(
            PaymentFailedEvent event
    ) {
        kafkaTemplate.send(
                "swiftpay.payment.failed",
                event.transactionId(),
                event
        );
    }
}
