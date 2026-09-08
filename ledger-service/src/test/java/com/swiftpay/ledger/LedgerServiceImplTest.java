package com.swiftpay.ledger;

import com.swiftpay.ledger.domain.entity.Account;
import com.swiftpay.ledger.domain.entity.LedgerEntry;
import com.swiftpay.ledger.domain.enums.EntryType;
import com.swiftpay.ledger.domain.event.PaymentCompletedEvent;
import com.swiftpay.ledger.domain.event.PaymentFailedEvent;
import com.swiftpay.ledger.domain.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.exception.InsufficientBalanceException;
import com.swiftpay.ledger.messaging.producers.PaymentResultProducer;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.ledger.service.impl.LedgerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private PaymentResultProducer paymentResultProducer;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    @Test
    void shouldProcessSuccessfulTransfer() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("200.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver));
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-100", sender.getId())).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-100", receiver.getId())).thenReturn(false);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-100",
                1001L,
                1002L,
                new BigDecimal("300.00"),
                "USD"
        );

        ledgerService.processPayment(event);

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));

        ArgumentCaptor<PaymentCompletedEvent> completedCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentResultProducer).publishCompleted(completedCaptor.capture());
        assertThat(completedCaptor.getValue().transactionId()).isEqualTo("TXN-100");
        assertThat(completedCaptor.getValue().amount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void shouldPublishFailureForInsufficientBalanceAndAvoidPartialLedgerEntries() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("50.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver));
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-200", sender.getId())).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-200", receiver.getId())).thenReturn(false);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-200",
                1001L,
                1002L,
                new BigDecimal("250.00"),
                "USD"
        );

        ledgerService.processPayment(event);

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));

        ArgumentCaptor<PaymentFailedEvent> failedCaptor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentResultProducer).publishFailed(failedCaptor.capture());
        assertThat(failedCaptor.getValue().transactionId()).isEqualTo("TXN-200");
        assertThat(failedCaptor.getValue().reason()).contains("Insufficient balance");
    }

    @Test
    void shouldIgnoreDuplicatePaymentInitiatedEvent() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("200.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver));
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-300", sender.getId())).thenReturn(true);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-300", receiver.getId())).thenReturn(true);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-300",
                1001L,
                1002L,
                new BigDecimal("500.00"),
                "USD"
        );

        ledgerService.processPayment(event);

        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        verify(paymentResultProducer, never()).publishCompleted(any(PaymentCompletedEvent.class));
        verify(paymentResultProducer, never()).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void shouldRetryOptimisticLockFailureAndEventuallySucceed() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account senderRetry = new Account();
        senderRetry.setUserId(1001L);
        senderRetry.setCurrency("USD");
        senderRetry.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(senderRetry, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("200.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        Account receiverRetry = new Account();
        receiverRetry.setUserId(1002L);
        receiverRetry.setCurrency("USD");
        receiverRetry.setBalance(new BigDecimal("200.00"));
        ReflectionTestUtils.setField(receiverRetry, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender), Optional.of(senderRetry));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver), Optional.of(receiverRetry));
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-400", sender.getId())).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-400", receiver.getId())).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-400", senderRetry.getId())).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId("TXN-400", receiverRetry.getId())).thenReturn(false);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("conflict", "retry-me"))
                .thenReturn(null);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-400",
                1001L,
                1002L,
                new BigDecimal("150.00"),
                "USD"
        );

        ledgerService.processPayment(event);

        verify(ledgerEntryRepository, atLeast(2)).save(any(LedgerEntry.class));
        assertThat(senderRetry.getBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(receiverRetry.getBalance()).isEqualByComparingTo(new BigDecimal("350.00"));
        verify(paymentResultProducer).publishCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void shouldRejectSecondPaymentWhenSenderBalanceCannotCoverIt() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver));
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId(eq("TXN-500"), eq(1L))).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId(eq("TXN-500"), eq(2L))).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId(eq("TXN-501"), eq(1L))).thenReturn(false);
        when(ledgerEntryRepository.existsByTransactionIdAndAccountId(eq("TXN-501"), eq(2L))).thenReturn(false);

        ledgerService.processPayment(new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-500",
                1001L,
                1002L,
                new BigDecimal("700.00"),
                "USD"
        ));

        sender.setBalance(new BigDecimal("300.00"));
        ledgerService.processPayment(new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-501",
                1001L,
                1002L,
                new BigDecimal("700.00"),
                "USD"
        ));

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        verify(paymentResultProducer, times(1)).publishCompleted(any(PaymentCompletedEvent.class));
        verify(paymentResultProducer, times(1)).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void shouldThrowForCurrencyMismatchWithoutChangingBalance() {
        Account sender = new Account();
        sender.setUserId(1001L);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(sender, "id", 1L);

        Account receiver = new Account();
        receiver.setUserId(1002L);
        receiver.setCurrency("EUR");
        receiver.setBalance(new BigDecimal("200.00"));
        ReflectionTestUtils.setField(receiver, "id", 2L);

        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByUserId(1002L)).thenReturn(Optional.of(receiver));

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-600",
                1001L,
                1002L,
                new BigDecimal("100.00"),
                "USD"
        );

        assertThatThrownBy(() -> ledgerService.processPayment(event))
                .isInstanceOf(RuntimeException.class);

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void shouldRejectMissingAccountWithoutChangingBalances() {
        when(accountRepository.findByUserId(1001L)).thenReturn(Optional.empty());

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                java.util.UUID.randomUUID(),
                "TXN-700",
                1001L,
                1002L,
                new BigDecimal("100.00"),
                "USD"
        );

        assertThatThrownBy(() -> ledgerService.processPayment(event))
                .isInstanceOf(RuntimeException.class);

        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        verify(paymentResultProducer, never()).publishCompleted(any(PaymentCompletedEvent.class));
        verify(paymentResultProducer, never()).publishFailed(any(PaymentFailedEvent.class));
    }
}
