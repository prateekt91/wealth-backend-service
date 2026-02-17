package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.CleanLedgerEntry;
import com.wealthmanager.backend.model.PortfolioHolding;
import com.wealthmanager.backend.model.RawIngestion;
import com.wealthmanager.backend.repository.RawIngestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Gmail/SMS raw ingestion for stocks and mutual fund holdings/ledger entries,
 * and stores structured Clean Ledger and portfolio state.
 * Can be extended with LLM-based extraction for better accuracy.
 */
@Service
@Slf4j
public class HoldingsParsingService {

    private static final Pattern SIP_OR_PURCHASE = Pattern.compile(
            "(?:purchased|bought|SIP|redeemed|sold)\\s+([\\d.,]+)\\s+(?:units?|shares?)\\s+(?:of\\s+)?([A-Za-z0-9\\s]+?)(?:\\s+at|\\s+for|\\s+@|$)",
            Pattern.CASE_INSENSITIVE);

    private final RawIngestionRepository rawIngestionRepository;
    private final CleanLedgerService cleanLedgerService;
    private final PortfolioService portfolioService;

    public HoldingsParsingService(RawIngestionRepository rawIngestionRepository,
                                 CleanLedgerService cleanLedgerService,
                                 PortfolioService portfolioService) {
        this.rawIngestionRepository = rawIngestionRepository;
        this.cleanLedgerService = cleanLedgerService;
        this.portfolioService = portfolioService;
    }

    @Async
    @Transactional
    public void processAsync(Long rawIngestionId) {
        rawIngestionRepository.findById(rawIngestionId).ifPresent(this::process);
    }

    /**
     * Parse raw message and persist any extracted ledger entries and portfolio updates.
     * Uses simple regex; can be replaced or complemented with LLM-based parsing.
     */
    protected void process(RawIngestion raw) {
        String body = raw.getRawBody();
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            Matcher m = SIP_OR_PURCHASE.matcher(body);
            while (m.find()) {
                String qtyStr = m.group(1).replace(",", "");
                String nameOrSymbol = m.group(2).trim();
                if (nameOrSymbol.length() > 100) {
                    nameOrSymbol = nameOrSymbol.substring(0, 100);
                }
                try {
                    BigDecimal qty = new BigDecimal(qtyStr);
                    if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    boolean isSell = body.substring(0, Math.max(0, m.start())).toLowerCase().contains("sold")
                            || body.substring(0, Math.max(0, m.start())).toLowerCase().contains("redeemed");
                    String entryType = isSell ? "REDEMPTION" : "SIP";
                    String instrumentType = inferInstrumentType(body, nameOrSymbol);
                    LocalDateTime ledgerDate = raw.getReceivedAt() != null ? raw.getReceivedAt() : LocalDateTime.now();

                    CleanLedgerEntry entry = CleanLedgerEntry.builder()
                            .rawIngestionId(raw.getId())
                            .entryType(entryType)
                            .instrumentType(instrumentType)
                            .symbol(nameOrSymbol.length() > 50 ? null : nameOrSymbol)
                            .name(nameOrSymbol)
                            .quantity(qty)
                            .price(null)
                            .amount(BigDecimal.ZERO)
                            .currency("INR")
                            .ledgerDate(ledgerDate)
                            .description("Parsed from " + raw.getSource())
                            .build();
                    cleanLedgerService.saveEntry(entry);

                    if (!isSell) {
                        PortfolioHolding holding = PortfolioHolding.builder()
                                .rawIngestionId(raw.getId())
                                .instrumentType(instrumentType)
                                .symbol(nameOrSymbol.length() > 50 ? null : nameOrSymbol)
                                .name(nameOrSymbol)
                                .quantity(qty)
                                .averagePrice(null)
                                .currentValue(null)
                                .currency("INR")
                                .lastUpdated(ledgerDate)
                                .build();
                        portfolioService.upsertHolding(holding);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Skip non-numeric quantity: {}", qtyStr);
                }
            }
        } catch (Exception e) {
            log.warn("Holdings parsing failed for raw_ingestion_id={}: {}", raw.getId(), e.getMessage());
        }
    }

    private String inferInstrumentType(String body, String nameOrSymbol) {
        String lower = body.toLowerCase();
        if (lower.contains("mutual fund") || lower.contains("mf") || lower.contains("sip") || lower.contains("nav")) {
            return PortfolioService.INSTRUMENT_MUTUAL_FUND;
        }
        if (lower.contains("share") || lower.contains("stock") || lower.contains("equity") || lower.contains("nse") || lower.contains("bse")) {
            return PortfolioService.INSTRUMENT_STOCK;
        }
        return PortfolioService.INSTRUMENT_MUTUAL_FUND;
    }
}
