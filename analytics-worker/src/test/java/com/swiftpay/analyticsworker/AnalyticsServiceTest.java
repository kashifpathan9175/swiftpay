package com.swiftpay.analyticsworker;

import com.swiftpay.analyticsworker.domain.event.PaymentCompletedEvent;
import com.swiftpay.analyticsworker.exception.InvalidPaymentCompletedEventException;
import com.swiftpay.analyticsworker.repository.AnalyticsPaymentEventRepository;
import com.swiftpay.analyticsworker.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AnalyticsService.class)
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AnalyticsPaymentEventRepository repository;

    @Test
    void shouldPersistValidPaymentCompletedEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                "TXN-100",
                10L,
                20L,
                new BigDecimal("250.00"),
                "USD"
        );

        analyticsService.processCompletedPayment(event);

        assertThat(repository.count()).isEqualTo(1L);
        assertThat(repository.existsByEventId(event.eventId())).isTrue();
    }

    @Test
    void shouldIgnoreDuplicatePaymentCompletedEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                "TXN-200",
                11L,
                21L,
                new BigDecimal("100.00"),
                "EUR"
        );

        analyticsService.processCompletedPayment(event);
        analyticsService.processCompletedPayment(event);

        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void shouldRejectInvalidEvent() {
        assertThatThrownBy(() -> analyticsService.processCompletedPayment(null))
                .isInstanceOf(InvalidPaymentCompletedEventException.class);

        assertThatThrownBy(() -> analyticsService.processCompletedPayment(
                new PaymentCompletedEvent(null, "TXN-300", 1L, 2L, new BigDecimal("10.00"), "USD")))
                .isInstanceOf(InvalidPaymentCompletedEventException.class);
    }

    @Test
    void shouldCalculateSummaryByCurrency() {
        analyticsService.processCompletedPayment(
                new PaymentCompletedEvent(UUID.randomUUID(), "TXN-301", 1L, 2L, new BigDecimal("100.00"), "USD"));
        analyticsService.processCompletedPayment(
                new PaymentCompletedEvent(UUID.randomUUID(), "TXN-302", 3L, 4L, new BigDecimal("50.00"), "USD"));
        analyticsService.processCompletedPayment(
                new PaymentCompletedEvent(UUID.randomUUID(), "TXN-303", 5L, 6L, new BigDecimal("25.00"), "EUR"));

        var summary = analyticsService.getSummary();

        assertThat(summary.totalCompletedTransactions()).isEqualTo(3L);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("175.00"));
        assertThat(summary.transactionCountByCurrency()).containsEntry("USD", 2L).containsEntry("EUR", 1L);
    }
}
