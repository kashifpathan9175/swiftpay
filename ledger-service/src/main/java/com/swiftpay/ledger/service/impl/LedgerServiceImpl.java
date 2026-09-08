package com.swiftpay.ledger.service.impl;

import com.swiftpay.ledger.domain.entity.Account;
import com.swiftpay.ledger.domain.entity.LedgerEntry;
import com.swiftpay.ledger.domain.enums.EntryType;
import com.swiftpay.ledger.domain.event.PaymentCompletedEvent;
import com.swiftpay.ledger.domain.event.PaymentFailedEvent;
import com.swiftpay.ledger.domain.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.exception.AccountNotFoundException;
import com.swiftpay.ledger.exception.CurrencyMismatchException;
import com.swiftpay.ledger.exception.DuplicateLedgerEntryException;
import com.swiftpay.ledger.exception.InsufficientBalanceException;
import com.swiftpay.ledger.exception.InvalidPaymentEventException;
import com.swiftpay.ledger.messaging.producers.PaymentResultProducer;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.ledger.service.LedgerService;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log =
            LoggerFactory.getLogger(LedgerServiceImpl.class);

    private static final int CURRENCY_LENGTH = 3;
    private static final int MAX_AMOUNT_SCALE = 4;
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 3;
    private static final long OPTIMISTIC_LOCK_BACKOFF_MS = 100L;
    private static final String PAYMENT_COMPLETED_TOPIC =
            "swiftpay.payment.completed";
    private static final String PAYMENT_FAILED_TOPIC =
            "swiftpay.payment.failed";

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentResultProducer paymentResultProducer;

    public LedgerServiceImpl(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            PaymentResultProducer paymentResultProducer
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentResultProducer = paymentResultProducer;
    }

    @Override
    public void processPayment(PaymentInitiatedEvent event) {
        processPaymentWithRetry(event, MAX_OPTIMISTIC_LOCK_RETRIES);
    }

    private void processPaymentWithRetry(
            PaymentInitiatedEvent event,
            int remainingRetries
    ) {
        try {
            processPaymentInTransaction(event);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            if (remainingRetries <= 1) {
                log.error(
                        "Optimistic locking failure persisted for transactionId={} after {} attempts",
                        event.transactionId(),
                        MAX_OPTIMISTIC_LOCK_RETRIES,
                        ex
                );
                throw ex;
            }

            long backoffMs = OPTIMISTIC_LOCK_BACKOFF_MS * (MAX_OPTIMISTIC_LOCK_RETRIES - remainingRetries + 1L);
            log.warn(
                    "Optimistic lock conflict while processing transactionId={}, retrying in {} ms, attemptsLeft={}",
                    event.transactionId(),
                    backoffMs,
                    remainingRetries - 1
            );

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while retrying optimistic lock conflict for transactionId="
                                + event.transactionId(),
                        interruptedException
                );
            }

            processPaymentWithRetry(event, remainingRetries - 1);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processPaymentInTransaction(PaymentInitiatedEvent event) {

        validateEvent(event);

        String transactionId = event.transactionId();

        log.info(
                "Processing payment event. eventId={}, transactionId={}, senderId={}, receiverId={}, amount={}, currency={}",
                event.eventId(),
                transactionId,
                event.senderId(),
                event.receiverId(),
                event.amount(),
                event.currency()
        );

        Account sender = findAccount(event.senderId());
        Account receiver = findAccount(event.receiverId());

        if (isAlreadyProcessed(
                transactionId,
                sender.getId(),
                receiver.getId()
        )) {
            log.info(
                    "Payment already processed. Ignoring duplicate event. eventId={}, transactionId={}",
                    event.eventId(),
                    transactionId
            );
            return;
        }

        validateCurrency(event, sender, receiver);

        try {
            debitSender(sender, event.amount(), transactionId);
        } catch (InsufficientBalanceException ex) {
            log.warn(
                    "Insufficient balance. transactionId={}, senderAccountId={}, amount={}",
                    transactionId,
                    sender.getId(),
                    event.amount()
            );
            publishFailedAfterCommit(event, ex.getMessage());
            return;
        }

        creditReceiver(receiver, event.amount(), transactionId);

        LedgerEntry debitEntry = createLedgerEntry(
                transactionId,
                sender,
                EntryType.DEBIT,
                event.amount(),
                sender.getBalance()
        );

        LedgerEntry creditEntry = createLedgerEntry(
                transactionId,
                receiver,
                EntryType.CREDIT,
                event.amount(),
                receiver.getBalance()
        );

        saveLedgerEntries(
                debitEntry,
                creditEntry,
                transactionId,
                sender.getId(),
                receiver.getId()
        );

        publishCompletedAfterCommit(event);

        log.info(
                "Payment successfully posted to ledger. eventId={}, transactionId={}, senderAccountId={}, receiverAccountId={}",
                event.eventId(),
                transactionId,
                sender.getId(),
                receiver.getId()
        );
    }

    /**
     * Finds an account using the business-level user ID.
     */
    private Account findAccount(Long userId) {

        return accountRepository
                .findByUserId(userId)
                .orElseThrow(() -> {

                    log.warn(
                            "Account not found. userId={}",
                            userId
                    );

                    return new AccountNotFoundException(
                            "Account not found for userId=" + userId
                    );
                });
    }

    /**
     * Checks whether both sides of the ledger have already
     * been created for this transaction.
     *
     * A valid payment must contain:
     *
     * DEBIT  -> sender
     * CREDIT -> receiver
     *
     * If only one exists, the database is already in an
     * inconsistent state and we must not silently continue.
     */
    private boolean isAlreadyProcessed(
            String transactionId,
            Long senderAccountId,
            Long receiverAccountId
    ) {

        boolean senderEntryExists =
                ledgerEntryRepository
                        .existsByTransactionIdAndAccountId(
                                transactionId,
                                senderAccountId
                        );

        boolean receiverEntryExists =
                ledgerEntryRepository
                        .existsByTransactionIdAndAccountId(
                                transactionId,
                                receiverAccountId
                        );

        if (senderEntryExists && receiverEntryExists) {

            return true;
        }

        if (senderEntryExists || receiverEntryExists) {

            log.error(
                    "Inconsistent ledger state detected. transactionId={}, senderAccountId={}, receiverAccountId={}, senderEntryExists={}, receiverEntryExists={}",
                    transactionId,
                    senderAccountId,
                    receiverAccountId,
                    senderEntryExists,
                    receiverEntryExists
            );

            throw new IllegalStateException(
                    "Inconsistent ledger state for transactionId="
                            + transactionId
            );
        }

        return false;
    }

    /**
     * Ensures that the payment currency matches both accounts.
     */
    private void validateCurrency(
            PaymentInitiatedEvent event,
            Account sender,
            Account receiver
    ) {

        String paymentCurrency =
                normalizeCurrency(event.currency());

        String senderCurrency =
                normalizeCurrency(sender.getCurrency());

        String receiverCurrency =
                normalizeCurrency(receiver.getCurrency());

        if (!paymentCurrency.equals(senderCurrency)) {

            log.warn(
                    "Sender currency mismatch. transactionId={}, senderId={}, paymentCurrency={}, accountCurrency={}",
                    event.transactionId(),
                    event.senderId(),
                    paymentCurrency,
                    senderCurrency
            );

            throw new CurrencyMismatchException(
                    "Sender account currency does not match payment currency"
            );
        }

        if (!paymentCurrency.equals(receiverCurrency)) {

            log.warn(
                    "Receiver currency mismatch. transactionId={}, receiverId={}, paymentCurrency={}, accountCurrency={}",
                    event.transactionId(),
                    event.receiverId(),
                    paymentCurrency,
                    receiverCurrency
            );

            throw new CurrencyMismatchException(
                    "Receiver account currency does not match payment currency"
            );
        }
    }

    /**
     * Debits the sender account.
     */
    private void debitSender(
            Account sender,
            BigDecimal amount,
            String transactionId
    ) {

        try {

            sender.debit(amount);

        } catch (InsufficientBalanceException ex) {

            log.warn(
                    "Insufficient balance. transactionId={}, senderAccountId={}, amount={}",
                    transactionId,
                    sender.getId(),
                    amount
            );

            throw ex;
        }
    }

    /**
     * Credits the receiver account.
     */
    private void creditReceiver(
            Account receiver,
            BigDecimal amount,
            String transactionId
    ) {

        try {

            receiver.credit(amount);

        } catch (IllegalArgumentException ex) {

            log.error(
                    "Failed to credit receiver account. transactionId={}, receiverAccountId={}, amount={}",
                    transactionId,
                    receiver.getId(),
                    amount,
                    ex
            );

            throw new InvalidPaymentEventException(
                    "Invalid payment amount"
            );
        }
    }

    /**
     * Creates a ledger entry representing the account movement.
     */
    private LedgerEntry createLedgerEntry(
            String transactionId,
            Account account,
            EntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {

        LedgerEntry entry = new LedgerEntry();

        entry.setTransactionId(transactionId);
        entry.setAccount(account);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);

        return entry;
    }

    /**
     * Persists both ledger entries.
     *
     * flush() forces Hibernate to execute the SQL before
     * returning from this method, allowing database constraints
     * to be detected immediately.
     */
    private void saveLedgerEntries(
            LedgerEntry debitEntry,
            LedgerEntry creditEntry,
            String transactionId,
            Long senderAccountId,
            Long receiverAccountId
    ) {

        try {
            ledgerEntryRepository.save(debitEntry);
            ledgerEntryRepository.save(creditEntry);
            ledgerEntryRepository.flush();

        } catch (DataIntegrityViolationException ex) {
            if (isAlreadyProcessed(
                    transactionId,
                    senderAccountId,
                    receiverAccountId
            )) {
                log.info(
                        "Ledger entries already exist for duplicate transactionId={}, senderAccountId={}, receiverAccountId={}",
                        transactionId,
                        senderAccountId,
                        receiverAccountId
                );
                return;
            }

            log.error(
                    "Database constraint violation while creating ledger entries. transactionId={}",
                    transactionId,
                    ex
            );

            throw new DuplicateLedgerEntryException(
                    "Unable to create ledger entries for transactionId="
                            + transactionId
            );
        }
    }

    /**
     * Validates the event received from Kafka.
     */
    private void validateEvent(PaymentInitiatedEvent event) {

        if (event == null) {

            throw new InvalidPaymentEventException(
                    "Payment event must not be null"
            );
        }

        if (event.eventId() == null) {

            throw new InvalidPaymentEventException(
                    "eventId must not be null"
            );
        }

        if (event.transactionId() == null ||
                event.transactionId().isBlank()) {

            throw new InvalidPaymentEventException(
                    "transactionId must not be blank"
            );
        }

        if (event.transactionId().trim().length() > 64) {

            throw new InvalidPaymentEventException(
                    "transactionId must not exceed 64 characters"
            );
        }

        if (event.senderId() == null) {

            throw new InvalidPaymentEventException(
                    "senderId must not be null"
            );
        }

        if (event.receiverId() == null) {

            throw new InvalidPaymentEventException(
                    "receiverId must not be null"
            );
        }

        if (Objects.equals(
                event.senderId(),
                event.receiverId()
        )) {

            throw new InvalidPaymentEventException(
                    "senderId and receiverId must be different"
            );
        }

        if (event.amount() == null ||
                event.amount().signum() <= 0) {

            throw new InvalidPaymentEventException(
                    "amount must be greater than zero"
            );
        }

        if (event.amount().scale() > MAX_AMOUNT_SCALE) {

            throw new InvalidPaymentEventException(
                    "amount must have at most "
                            + MAX_AMOUNT_SCALE
                            + " decimal places"
            );
        }

        validateCurrency(event.currency());
    }

    /**
     * Validates and normalizes currency.
     */
    private void validateCurrency(String currency) {

        if (currency == null || currency.isBlank()) {

            throw new InvalidPaymentEventException(
                    "currency must not be blank"
            );
        }

        if (currency.trim().length() != CURRENCY_LENGTH) {

            throw new InvalidPaymentEventException(
                    "currency must contain exactly "
                            + CURRENCY_LENGTH
                            + " characters"
            );
        }
    }

    private void publishCompletedAfterCommit(PaymentInitiatedEvent event) {
        PaymentCompletedEvent completedEvent =
                new PaymentCompletedEvent(
                        event.eventId(),
                        event.transactionId(),
                        event.senderId(),
                        event.receiverId(),
                        event.amount(),
                        event.currency()
                );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            paymentResultProducer.publishCompleted(completedEvent);
                            log.info(
                                    "Published PaymentCompleted event. transactionId={}",
                                    event.transactionId()
                            );
                        }
                    }
            );
            return;
        }

        paymentResultProducer.publishCompleted(completedEvent);
        log.info(
                "Published PaymentCompleted event. transactionId={}",
                event.transactionId()
        );
    }

    private void publishFailedAfterCommit(
            PaymentInitiatedEvent event,
            String reason
    ) {
        PaymentFailedEvent failedEvent =
                new PaymentFailedEvent(
                        event.eventId(),
                        event.transactionId(),
                        event.senderId(),
                        event.receiverId(),
                        event.amount(),
                        event.currency(),
                        reason
                );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            paymentResultProducer.publishFailed(failedEvent);
                            log.info(
                                    "Published PaymentFailed event. transactionId={}, reason={}",
                                    event.transactionId(),
                                    reason
                            );
                        }
                    }
            );
            return;
        }

        paymentResultProducer.publishFailed(failedEvent);
        log.info(
                "Published PaymentFailed event. transactionId={}, reason={}",
                event.transactionId(),
                reason
        );
    }



    /**
     * Normalizes currency into ISO-style uppercase representation.
     */
    private String normalizeCurrency(String currency) {

        validateCurrency(currency);

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}