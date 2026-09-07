package com.swiftpay.transactiongateway;

import com.swiftpay.transactiongateway.domain.entities.OutboxEvent;
import com.swiftpay.transactiongateway.domain.enums.OutboxStatus;
import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class OutboxEventTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private OutboxEvent createEvent() {
        return OutboxEvent.pending(
                ID,
                "PAYMENT",
                "TXN-123",
                "PaymentInitiated",
                "payment.initiated",
                "{\"transactionId\":\"TXN-123\"}",
                NOW
        );
    }

    @Test
    public void shouldCreatePendingEvent() {
        OutboxEvent event = createEvent();

        assertThat(event.getId()).isEqualTo(ID);
        assertThat(event.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(event.getAggregateId()).isEqualTo("TXN-123");
        assertThat(event.getEventType()).isEqualTo("PaymentInitiated");
        assertThat(event.getTopic()).isEqualTo("payment.initiated");
        assertThat(event.getPayload())
                .isEqualTo("{\"transactionId\":\"TXN-123\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getAvailableAt()).isEqualTo(NOW);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    public void shouldIncrementAttempts() {
        OutboxEvent event = createEvent();

        event.markAttempt();
        event.markAttempt();

        assertThat(event.getAttempts()).isEqualTo(2);
    }

    @Test
    public void shouldMarkEventPublished() {
        OutboxEvent event = createEvent();
        Instant publishedAt = Instant.now();

        event.markPublished(publishedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    public void shouldNotAllowAttemptAfterPublished() {
        OutboxEvent event = createEvent();

        event.markPublished(Instant.now());

        assertThatThrownBy(event::markAttempt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Cannot increment attempts for a published event"
                );
    }

    @Test
    public void shouldNotAllowFailAfterPublished() {
        OutboxEvent event = createEvent();

        event.markPublished(Instant.now());

        assertThatThrownBy(event::markFailed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Cannot fail an already published event"
                );
    }

    @Test
    public void shouldReschedulePendingEvent() {
        OutboxEvent event = createEvent();
        Instant nextAttempt = NOW.plusSeconds(30);

        event.markAttempt();
        event.markFailed();
        event.reschedule(nextAttempt);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getAvailableAt()).isEqualTo(nextAttempt);
    }

    @Test
    public void shouldRejectBlankAggregateType() {
        assertThatThrownBy(() ->
                OutboxEvent.pending(
                        ID,
                        " ",
                        "TXN-123",
                        "PaymentInitiated",
                        "payment.initiated",
                        "{}",
                        NOW
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType must not be null or blank");
    }

    @Test
    public void shouldRejectBlankPayload() {
        assertThatThrownBy(() ->
                OutboxEvent.pending(
                        ID,
                        "PAYMENT",
                        "TXN-123",
                        "PaymentInitiated",
                        "payment.initiated",
                        " ",
                        NOW
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload must not be null or blank");
    }

    @Test
    public void shouldRejectNullAvailableAt() {
        assertThatThrownBy(() ->
                OutboxEvent.pending(
                        ID,
                        "PAYMENT",
                        "TXN-123",
                        "PaymentInitiated",
                        "payment.initiated",
                        "{}",
                        null
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("availableAt must not be null");
    }
}
