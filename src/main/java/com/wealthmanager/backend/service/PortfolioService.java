package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.PortfolioHolding;
import com.wealthmanager.backend.model.dto.PortfolioHoldingResponse;
import com.wealthmanager.backend.repository.PortfolioHoldingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class PortfolioService {

    public static final String INSTRUMENT_STOCK = "STOCK";
    public static final String INSTRUMENT_MUTUAL_FUND = "MUTUAL_FUND";

    private final PortfolioHoldingRepository portfolioHoldingRepository;

    public PortfolioService(PortfolioHoldingRepository portfolioHoldingRepository) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
    }

    public List<PortfolioHoldingResponse> getAllHoldings() {
        return portfolioHoldingRepository.findAllByOrderByInstrumentTypeAscSymbolAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PortfolioHoldingResponse> getHoldingsByInstrumentType(String instrumentType) {
        return portfolioHoldingRepository.findByInstrumentTypeOrderBySymbolAsc(instrumentType)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PortfolioHoldingResponse> getStockHoldings() {
        return getHoldingsByInstrumentType(INSTRUMENT_STOCK);
    }

    public List<PortfolioHoldingResponse> getMutualFundHoldings() {
        return getHoldingsByInstrumentType(INSTRUMENT_MUTUAL_FUND);
    }

    public Map<String, Object> getPortfolioSummary() {
        List<PortfolioHolding> all = portfolioHoldingRepository.findAllByOrderByInstrumentTypeAscSymbolAsc();
        long stockCount = all.stream().filter(h -> INSTRUMENT_STOCK.equals(h.getInstrumentType())).count();
        long mfCount = all.stream().filter(h -> INSTRUMENT_MUTUAL_FUND.equals(h.getInstrumentType())).count();
        java.math.BigDecimal totalValue = all.stream()
                .map(PortfolioHolding::getCurrentValue)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalHoldings", all.size());
        summary.put("stockCount", stockCount);
        summary.put("mutualFundCount", mfCount);
        summary.put("totalCurrentValue", totalValue);
        return summary;
    }

    @Transactional
    public PortfolioHoldingResponse upsertHolding(PortfolioHolding holding) {
        String symbol = holding.getSymbol() != null ? holding.getSymbol() : "";
        Optional<PortfolioHolding> existing = portfolioHoldingRepository.findByInstrumentTypeAndSymbol(
                holding.getInstrumentType(), symbol);
        PortfolioHolding toSave;
        if (existing.isPresent()) {
            PortfolioHolding e = existing.get();
            e.setQuantity(e.getQuantity().add(holding.getQuantity()));
            if (holding.getAveragePrice() != null) e.setAveragePrice(holding.getAveragePrice());
            if (holding.getCurrentValue() != null) e.setCurrentValue(holding.getCurrentValue());
            if (holding.getName() != null) e.setName(holding.getName());
            e.setLastUpdated(LocalDateTime.now());
            e.setRawIngestionId(holding.getRawIngestionId());
            toSave = portfolioHoldingRepository.save(e);
        } else {
            toSave = portfolioHoldingRepository.save(holding);
        }
        return toResponse(toSave);
    }

    private PortfolioHoldingResponse toResponse(PortfolioHolding h) {
        return new PortfolioHoldingResponse(
                h.getId(),
                h.getRawIngestionId(),
                h.getInstrumentType(),
                h.getSymbol(),
                h.getName(),
                h.getQuantity(),
                h.getAveragePrice(),
                h.getCurrentValue(),
                h.getCurrency(),
                h.getLastUpdated(),
                h.getCreatedAt()
        );
    }
}
