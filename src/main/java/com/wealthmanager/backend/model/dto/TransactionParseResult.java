package com.wealthmanager.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of parsing raw SMS/email text into a transaction.
 * Used by LLM parser with structured output (JSON schema).
 */
public record TransactionParseResult(
        @JsonProperty(required = true) BigDecimal amount,
        @JsonProperty(defaultValue = "INR") String currency,
        String merchantName,
        String category,
        @JsonProperty(required = true) String transactionType,
        @JsonProperty LocalDateTime transactionDate,
        String description
) {
    /** DEBIT or CREDIT. */
    public String transactionType() {
        return transactionType == null ? null : transactionType.toUpperCase();
    }
}
