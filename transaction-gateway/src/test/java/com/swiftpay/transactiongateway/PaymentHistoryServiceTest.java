package com.swiftpay.transactiongateway;

import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;
import com.swiftpay.transactiongateway.domain.entities.Payment;
import com.swiftpay.transactiongateway.exception.InvalidPaymentException;
import com.swiftpay.transactiongateway.repository.PaymentRepository;
import com.swiftpay.transactiongateway.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void shouldReturnTransactionsForUserAsSender() {
        Long userId = 1001L;
        Payment payment = Payment.pending(
                "TXN-001",
                userId,
                1002L,
                new BigDecimal("100.00"),
                "USD"
        );

        PageRequest pageable = PageRequest.of(0, 20);
        Page<Payment> page = new PageImpl<>(List.of(payment), pageable, 1);

        when(paymentRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable))
                .thenReturn(page);

        Page<PaymentResponse> result = paymentService.getUserTransactions(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).transactionId()).isEqualTo("TXN-001");
        assertThat(result.getContent().get(0).senderId()).isEqualTo(userId);
    }

    @Test
    void shouldReturnTransactionsForUserAsReceiver() {
        Long userId = 1002L;
        Payment payment = Payment.pending(
                "TXN-002",
                1001L,
                userId,
                new BigDecimal("250.00"),
                "USD"
        );

        PageRequest pageable = PageRequest.of(0, 20);
        Page<Payment> page = new PageImpl<>(List.of(payment), pageable, 1);

        when(paymentRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable))
                .thenReturn(page);

        Page<PaymentResponse> result = paymentService.getUserTransactions(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).receiverId()).isEqualTo(userId);
    }

    @Test
    void shouldReturnTransactionsForUserInEitherRole() {
        Long userId = 1003L;
        Payment sent = Payment.pending(
                "TXN-003",
                userId,
                1004L,
                new BigDecimal("10.00"),
                "USD"
        );
        Payment received = Payment.pending(
                "TXN-004",
                1005L,
                userId,
                new BigDecimal("20.00"),
                "USD"
        );

        PageRequest pageable = PageRequest.of(0, 20);
        Page<Payment> page = new PageImpl<>(List.of(sent, received), pageable, 2);

        when(paymentRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable))
                .thenReturn(page);

        Page<PaymentResponse> result = paymentService.getUserTransactions(userId, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().stream().map(PaymentResponse::transactionId).toList())
                .containsExactlyInAnyOrder("TXN-003", "TXN-004");
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoTransactions() {
        Long userId = 999L;
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Payment> page = new PageImpl<>(List.of(), pageable, 0);

        when(paymentRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable))
                .thenReturn(page);

        Page<PaymentResponse> result = paymentService.getUserTransactions(userId, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldRejectInvalidUserId() {
        assertThatThrownBy(() -> paymentService.getUserTransactions(0L, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage("userId must be greater than zero");

        assertThatThrownBy(() -> paymentService.getUserTransactions(null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage("userId must be greater than zero");
    }

    @Test
    void shouldRejectInvalidPaginationParameters() {
        assertThatThrownBy(() -> paymentService.getUserTransactions(1001L, PageRequest.of(-1, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnNewestTransactionsFirst() {
        Long userId = 2001L;
        Payment older = Payment.pending(
                "TXN-100",
                500L,
                userId,
                new BigDecimal("30.00"),
                "USD"
        );
        Payment newer = Payment.pending(
                "TXN-101",
                userId,
                600L,
                new BigDecimal("40.00"),
                "USD"
        );

        PageRequest pageable = PageRequest.of(0, 20);
        Page<Payment> page = new PageImpl<>(List.of(newer, older), pageable, 2);

        when(paymentRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable))
                .thenReturn(page);

        Page<PaymentResponse> result = paymentService.getUserTransactions(userId, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).transactionId()).isEqualTo("TXN-101");
        assertThat(result.getContent().get(1).transactionId()).isEqualTo("TXN-100");
    }
}
