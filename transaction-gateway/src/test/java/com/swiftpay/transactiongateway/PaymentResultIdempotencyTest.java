package com.swiftpay.transactiongateway;

import com.swiftpay.transactiongateway.domain.entities.Payment;
import com.swiftpay.transactiongateway.domain.enums.PaymentStatus;
import com.swiftpay.transactiongateway.repository.OutboxEventRepository;
import com.swiftpay.transactiongateway.repository.PaymentRepository;
import com.swiftpay.transactiongateway.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentResultIdempotencyTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void shouldMarkPaymentCompleted() {
        Payment payment = Payment.pending(
                "TXN-123",
                1001L,
                1002L,
                new BigDecimal("100.00"),
                "USD"
        );

        when(paymentRepository.findByTransactionId("TXN-123"))
                .thenReturn(Optional.of(payment));

        paymentService.markCompleted("TXN-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void shouldMarkPaymentFailed() {
        Payment payment = Payment.pending(
                "TXN-456",
                1001L,
                1002L,
                new BigDecimal("50.00"),
                "USD"
        );

        when(paymentRepository.findByTransactionId("TXN-456"))
                .thenReturn(Optional.of(payment));

        paymentService.markFailed("TXN-456", "Insufficient balance");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient balance");
    }

    @Test
    void shouldIgnoreDuplicatePaymentCompletedEvent() {
        Payment payment = Payment.pending(
                "TXN-789",
                1001L,
                1002L,
                new BigDecimal("25.00"),
                "USD"
        );
        payment.markCompleted();

        when(paymentRepository.findByTransactionId("TXN-789"))
                .thenReturn(Optional.of(payment));

        paymentService.markCompleted("TXN-789");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void shouldIgnoreDuplicatePaymentFailedEvent() {
        Payment payment = Payment.pending(
                "TXN-101",
                1001L,
                1002L,
                new BigDecimal("75.00"),
                "USD"
        );
        payment.markFailed("Insufficient balance");

        when(paymentRepository.findByTransactionId("TXN-101"))
                .thenReturn(Optional.of(payment));

        paymentService.markFailed("TXN-101", "Insufficient balance");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient balance");
    }

    @Test
    void shouldIgnoreConflictingFailedResultAfterCompleted() {
        Payment payment = Payment.pending(
                "TXN-202",
                1001L,
                1002L,
                new BigDecimal("80.00"),
                "USD"
        );
        payment.markCompleted();

        when(paymentRepository.findByTransactionId("TXN-202"))
                .thenReturn(Optional.of(payment));

        paymentService.markFailed("TXN-202", "Insufficient balance");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void shouldIgnoreConflictingCompletedResultAfterFailed() {
        Payment payment = Payment.pending(
                "TXN-303",
                1001L,
                1002L,
                new BigDecimal("90.00"),
                "USD"
        );
        payment.markFailed("Insufficient balance");

        when(paymentRepository.findByTransactionId("TXN-303"))
                .thenReturn(Optional.of(payment));

        paymentService.markCompleted("TXN-303");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
