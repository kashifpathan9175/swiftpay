package com.swiftpay.transactiongateway;

import com.swiftpay.transactiongateway.domain.outbox.PaymentInitiatedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class PaymentInitiatedEventTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String TRANSACTION_ID = "TXN-123";
    private static final Long SENDER_ID = 1001L;
    private static final Long RECEIVER_ID = 1002L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final String CURRENCY = "INR";

    @Test
    void shouldCreateValidEvent() {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                EVENT_ID,
                TRANSACTION_ID,
                SENDER_ID,
                RECEIVER_ID,
                AMOUNT,
                CURRENCY
        );

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(event.senderId()).isEqualTo(SENDER_ID);
        assertThat(event.receiverId()).isEqualTo(RECEIVER_ID);
        assertThat(event.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(event.currency()).isEqualTo(CURRENCY);
    }

    @Test
    void shouldRejectNullEventId() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        null,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        AMOUNT,
                        CURRENCY
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventId must not be null");
    }

    @Test
    void shouldRejectBlankTransactionId() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        " ",
                        SENDER_ID,
                        RECEIVER_ID,
                        AMOUNT,
                        CURRENCY
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transactionId must not be null or blank");
    }

    @Test
    void shouldRejectNullSenderId() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        null,
                        RECEIVER_ID,
                        AMOUNT,
                        CURRENCY
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("senderId must not be null");
    }

    @Test
    void shouldRejectSameSenderAndReceiver() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        SENDER_ID,
                        AMOUNT,
                        CURRENCY
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("senderId and receiverId must be different");
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        null,
                        CURRENCY
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount must not be null");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        BigDecimal.ZERO,
                        CURRENCY
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        new BigDecimal("-10.00"),
                        CURRENCY
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        AMOUNT,
                        null
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency must not be null or blank");
    }

    @Test
    void shouldRejectInvalidCurrencyLength() {
        assertThatThrownBy(() ->
                new PaymentInitiatedEvent(
                        EVENT_ID,
                        TRANSACTION_ID,
                        SENDER_ID,
                        RECEIVER_ID,
                        AMOUNT,
                        "IN"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency must contain exactly 3 characters");
    }
}
