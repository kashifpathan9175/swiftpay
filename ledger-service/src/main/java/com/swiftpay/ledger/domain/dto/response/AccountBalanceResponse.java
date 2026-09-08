package com.swiftpay.ledger.domain.dto.response;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        Long userId,
        String currency,
        BigDecimal balance
) {
}
