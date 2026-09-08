package com.swiftpay.analyticsworker.repository;

import com.swiftpay.analyticsworker.domain.entity.AnalyticsPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsPaymentEventRepository extends JpaRepository<AnalyticsPaymentEvent, Long> {

    boolean existsByEventId(UUID eventId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM AnalyticsPaymentEvent e")
    BigDecimal sumTotalAmount();

    @Query("SELECT e.currency, COUNT(e) FROM AnalyticsPaymentEvent e GROUP BY e.currency")
    List<Object[]> countByCurrency();
}
