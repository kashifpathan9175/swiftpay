package com.swiftpay.transactiongateway.repository;

import com.swiftpay.transactiongateway.domain.entities.Payment;
import com.swiftpay.transactiongateway.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    Page<Payment> findBySenderIdOrReceiverId(
            Long senderId,
            Long receiverId,
            Pageable pageable
    );

    Page<Payment> findBySenderIdAndStatus(
            Long senderId,
            PaymentStatus status,
            Pageable pageable
    );

    Page<Payment> findByReceiverIdAndStatus(
            Long receiverId,
            PaymentStatus status,
            Pageable pageable
    );
}
