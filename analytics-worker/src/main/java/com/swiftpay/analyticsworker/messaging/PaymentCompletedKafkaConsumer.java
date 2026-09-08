package com.swiftpay.analyticsworker.messaging;

import com.swiftpay.analyticsworker.domain.event.PaymentCompletedEvent;
import com.swiftpay.analyticsworker.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedKafkaConsumer.class);

    private final AnalyticsService analyticsService;

    public PaymentCompletedKafkaConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(
            topics = "swiftpay.payment.completed",
            groupId = "${spring.kafka.consumer.group-id:analytics-worker-group}",
            containerFactory = "paymentCompletedKafkaListenerContainerFactory"
    )
    public void consume(PaymentCompletedEvent event) {
        log.info("Received payment completed event. transactionId={}, eventId={}",
                event.transactionId(), event.eventId());
        analyticsService.processCompletedPayment(event);
    }
}
