package com.swiftpay.analyticsworker.service;

import com.swiftpay.analyticsworker.domain.entity.AnalyticsPaymentEvent;
import com.swiftpay.analyticsworker.domain.event.PaymentCompletedEvent;
import com.swiftpay.analyticsworker.exception.InvalidPaymentCompletedEventException;
import com.swiftpay.analyticsworker.repository.AnalyticsPaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsPaymentEventRepository analyticsPaymentEventRepository;

    public AnalyticsService(AnalyticsPaymentEventRepository analyticsPaymentEventRepository) {
        this.analyticsPaymentEventRepository = analyticsPaymentEventRepository;
    }

    @Transactional
    public void processCompletedPayment(PaymentCompletedEvent event) {
        validate(event);

        if (analyticsPaymentEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate analytics event ignored. eventId={}, transactionId={}",
                    event.eventId(), event.transactionId());
            return;
        }

        AnalyticsPaymentEvent analyticsEvent = new AnalyticsPaymentEvent();
        analyticsEvent.setEventId(event.eventId());
        analyticsEvent.setTransactionId(event.transactionId());
        analyticsEvent.setSenderId(event.senderId());
        analyticsEvent.setReceiverId(event.receiverId());
        analyticsEvent.setAmount(event.amount());
        analyticsEvent.setCurrency(normalizeCurrency(event.currency()));

        try {
            analyticsPaymentEventRepository.saveAndFlush(analyticsEvent);
            log.info("Persisted analytics payment event. transactionId={}, eventId={}",
                    event.transactionId(), event.eventId());
        } catch (DataIntegrityViolationException duplicate) {
            if (analyticsPaymentEventRepository.existsByEventId(event.eventId())) {
                log.info("Duplicate analytics event after save conflict. eventId={}, transactionId={}",
                        event.eventId(), event.transactionId());
                return;
            }
            throw duplicate;
        }
    }

    public AnalyticsSummary getSummary() {
        long totalTransactions = analyticsPaymentEventRepository.count();
        BigDecimal totalAmount = analyticsPaymentEventRepository.sumTotalAmount();
        Map<String, Long> byCurrency = new HashMap<>();

        for (Object[] row : analyticsPaymentEventRepository.countByCurrency()) {
            String currency = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            byCurrency.put(currency, count);
        }

        return new AnalyticsSummary(
                totalTransactions,
                totalAmount == null ? BigDecimal.ZERO : totalAmount,
                byCurrency
        );
    }

    private void validate(PaymentCompletedEvent event) {
        if (event == null) {
            throw new InvalidPaymentCompletedEventException("PaymentCompletedEvent must not be null");
        }
        if (event.eventId() == null) {
            throw new InvalidPaymentCompletedEventException("eventId must not be null");
        }
        if (event.transactionId() == null || event.transactionId().isBlank()) {
            throw new InvalidPaymentCompletedEventException("transactionId must not be blank");
        }
        if (event.senderId() == null) {
            throw new InvalidPaymentCompletedEventException("senderId must not be null");
        }
        if (event.receiverId() == null) {
            throw new InvalidPaymentCompletedEventException("receiverId must not be null");
        }
        if (event.amount() == null || event.amount().signum() <= 0) {
            throw new InvalidPaymentCompletedEventException("amount must be greater than zero");
        }
        if (event.currency() == null || event.currency().isBlank()) {
            throw new InvalidPaymentCompletedEventException("currency must not be blank");
        }
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase();
    }
}
