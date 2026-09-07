package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(String transactionId);

    boolean existsByTransactionIdAndAccountId(
            String transactionId,
            Long accountId
    );
}
