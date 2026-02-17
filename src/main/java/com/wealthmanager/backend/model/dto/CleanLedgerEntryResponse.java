package com.wealthmanager.backend.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CleanLedgerEntryResponse(
        Long id,
        Long rawIngestionId,
        String entryType,
        String instrumentType,
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        String currency,
        LocalDateTime ledgerDate,
        String description,
        LocalDateTime createdAt
) {}
