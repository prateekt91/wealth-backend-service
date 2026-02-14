package com.wealthmanager.backend.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        String currency,
        String merchantName,
        String category,
        String transactionType,
        LocalDateTime transactionDate,
        String description,
        LocalDateTime createdAt
) {
}
