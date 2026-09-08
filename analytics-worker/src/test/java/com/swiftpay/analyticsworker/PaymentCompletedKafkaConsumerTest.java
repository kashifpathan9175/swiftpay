package com.swiftpay.analyticsworker;

import com.swiftpay.analyticsworker.domain.event.PaymentCompletedEvent;
import com.swiftpay.analyticsworker.messaging.PaymentCompletedKafkaConsumer;
import com.swiftpay.analyticsworker.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedKafkaConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private PaymentCompletedKafkaConsumer consumer;

    @Test
    void shouldDelegateIncomingEventToAnalyticsService() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                "TXN-999",
                60L,
                70L,
                new BigDecimal("15.00"),
                "USD"
        );

        consumer.consume(event);

        verify(analyticsService).processCompletedPayment(event);
    }
}
