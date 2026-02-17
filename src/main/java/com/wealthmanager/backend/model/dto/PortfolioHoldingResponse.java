package com.wealthmanager.backend.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioHoldingResponse(
        Long id,
        Long rawIngestionId,
        String instrumentType,
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentValue,
        String currency,
        LocalDateTime lastUpdated,
        LocalDateTime createdAt
) {}
