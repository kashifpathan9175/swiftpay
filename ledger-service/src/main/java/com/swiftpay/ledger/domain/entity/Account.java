package com.swiftpay.ledger.domain.entity;

import com.swiftpay.ledger.exception.InsufficientBalanceException;
import com.swiftpay.ledger.exception.InvalidPaymentEventException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "accounts",
        schema = "swift_pay_ledger",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_accounts_user_id",
                columnNames = "user_id"
        )
)

public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotBlank
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @PositiveOrZero
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    public Account() {
    }

    @PrePersist
    @PreUpdate
    public void touchTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void debit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Debit amount must be greater than zero"
            );
        }

        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance"
            );
        }

        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Credit amount must be greater than zero"
            );
        }

        balance = balance.add(amount);
    }
}
