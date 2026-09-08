package com.swiftpay.transactiongateway.domain.entities;

import com.swiftpay.transactiongateway.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        schema = "transaction_gateway"
)
public class OutboxEvent {


    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Version
    @Column(name = "event_version", nullable = false)
    private Long version;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // Required by JPA.
    }

    private OutboxEvent(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String payload,
            OutboxStatus status,
            int attempts,
            Instant availableAt,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateType = requireText(aggregateType, "aggregateType");
        this.aggregateId = requireText(aggregateId, "aggregateId");
        this.eventType = requireText(eventType, "eventType");
        this.topic = requireText(topic, "topic");
        this.payload = requireText(payload, "payload");
        this.status = Objects.requireNonNull(status, "status must not be null");

        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }

        this.attempts = attempts;
        this.availableAt = Objects.requireNonNull(
                availableAt,
                "availableAt must not be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
    }

    public static OutboxEvent pending(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String payload,
            Instant availableAt) {

        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                payload,
                OutboxStatus.PENDING,
                0,
                availableAt,
                Instant.now()
        );
    }

    @PrePersist
    private void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (availableAt == null) {
            availableAt = createdAt;
        }

        if (status == null) {
            status = OutboxStatus.PENDING;
        }
    }

    public void markAttempt() {
        if (status == OutboxStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Cannot increment attempts for a published event"
            );
        }

        attempts++;
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(
                publishedAt,
                "publishedAt must not be null"
        );
    }

    public void markFailed() {
        if (status == OutboxStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Cannot fail an already published event"
            );
        }

        this.status = OutboxStatus.FAILED;
    }

    public void reschedule(Instant nextAvailableAt) {
        if (status == OutboxStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Cannot reschedule a published event"
            );
        }

        this.status = OutboxStatus.PENDING;
        this.availableAt = Objects.requireNonNull(
                nextAvailableAt,
                "nextAvailableAt must not be null"
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank"
            );
        }

        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
