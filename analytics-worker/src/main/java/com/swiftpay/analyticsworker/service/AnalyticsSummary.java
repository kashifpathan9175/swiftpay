package com.swiftpay.analyticsworker.service;

import java.math.BigDecimal;
import java.util.Map;

public record AnalyticsSummary(
        long totalCompletedTransactions,
        BigDecimal totalAmount,
        Map<String, Long> transactionCountByCurrency
) {
}
