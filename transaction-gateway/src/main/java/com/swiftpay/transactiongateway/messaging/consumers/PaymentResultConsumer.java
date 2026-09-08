package com.swiftpay.transactiongateway.messaging.consumers;

import com.swiftpay.transactiongateway.domain.events.PaymentCompletedEvent;
import com.swiftpay.transactiongateway.domain.events.PaymentFailedEvent;
import com.swiftpay.transactiongateway.service.PaymentService;
import org.slf4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer {

    Logger log = org.slf4j.LoggerFactory.getLogger(PaymentResultConsumer.class);
    private final PaymentService paymentService;

    public PaymentResultConsumer(
            PaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "swiftpay.payment.completed",
            containerFactory = "paymentCompletedKafkaListenerContainerFactory"
    )
    public void consumeCompleted(
            PaymentCompletedEvent event
    ) {
        log.info(
                "Payment completed event received. transactionId={}",
                event.transactionId()
        );

        paymentService.markCompleted(
                event.transactionId()
        );
    }

    @KafkaListener(
            topics = "swiftpay.payment.failed",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    public void consumeFailed(
            PaymentFailedEvent event
    ) {

        log.info(
                "Payment failed event received. transactionId={}, reason={}",
                event.transactionId(),
                event.reason()
        );

        paymentService.markFailed(
                event.transactionId(),
                event.reason()
        );
    }
}
