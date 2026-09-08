package com.swiftpay.transactiongateway.domain.entities;

import com.swiftpay.transactiongateway.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "payments",
        schema = "transaction_gateway"
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "transaction_id",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String transactionId;

    @Column(
            name = "sender_id",
            nullable = false,
            updatable = false
    )
    private Long senderId;

    @Column(
            name = "receiver_id",
            nullable = false,
            updatable = false
    )
    private Long receiverId;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 4,
            updatable = false
    )
    private BigDecimal amount;

    @Column(
            name = "currency",
            nullable = false,
            length = 3,
            updatable = false
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 32
    )
    private PaymentStatus status;

    @Column(
            name = "failure_reason",
            length = 512
    )
    private String failureReason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected Payment() {
        // Required by JPA.
    }

    private Payment(
            String transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
            String currency
    ) {
        this.transactionId = requireText(transactionId, "transactionId");
        this.senderId = requirePositive(senderId, "senderId");
        this.receiverId = requirePositive(receiverId, "receiverId");

        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException(
                    "senderId and receiverId must be different"
            );
        }

        this.amount = requirePositiveAmount(amount);
        this.currency = normalizeCurrency(currency);
        this.status = PaymentStatus.PENDING;
    }

    public static Payment pending(
            String transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
            String currency
    ) {
        return new Payment(
                transactionId,
                senderId,
                receiverId,
                amount,
                currency
        );
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markProcessing() {
        if (status == PaymentStatus.COMPLETED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot move a terminal payment to PROCESSING"
            );
        }

        this.status = PaymentStatus.PROCESSING;
        this.failureReason = null;
    }

    public void markCompleted() {
        if (status == PaymentStatus.COMPLETED) {
            return;
        }

        if (status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot complete a payment that is already " + status
            );
        }

        if (status != PaymentStatus.PROCESSING) {
            this.status = PaymentStatus.PROCESSING;
        }

        this.status = PaymentStatus.COMPLETED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        if (status == PaymentStatus.FAILED) {
            return;
        }

        if (status == PaymentStatus.COMPLETED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot fail a payment that is already " + status
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "failure reason must not be blank"
            );
        }

        if (status != PaymentStatus.PROCESSING) {
            this.status = PaymentStatus.PROCESSING;
        }

        this.status = PaymentStatus.FAILED;
        this.failureReason = reason.length() > 512
                ? reason.substring(0, 512)
                : reason;
    }

    private void ensureStatusCanChange() {
        if (status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed payment cannot transition to another state"
            );
        }
        if (status == PaymentStatus.FAILED) {
            throw new IllegalStateException(
                    "Failed payment cannot transition to another state"
            );
        }
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank"
            );
        }

        return value.trim();
    }

    private static Long requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }

        return value;
    }

    private static BigDecimal requirePositiveAmount(
            BigDecimal amount
    ) {
        Objects.requireNonNull(
                amount,
                "amount must not be null"
        );

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero"
            );
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "amount must have at most 4 decimal places"
            );
        }

        return amount;
    }

    private static String normalizeCurrency(String currency) {
        String normalized = requireText(currency, "currency")
                .toUpperCase();

        if (normalized.length() != 3) {
            throw new IllegalArgumentException(
                    "currency must contain exactly 3 characters"
            );
        }

        return normalized;
    }


    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
