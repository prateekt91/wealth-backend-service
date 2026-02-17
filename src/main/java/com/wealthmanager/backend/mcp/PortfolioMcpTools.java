package com.wealthmanager.backend.mcp;

import com.wealthmanager.backend.model.dto.CleanLedgerEntryResponse;
import com.wealthmanager.backend.model.dto.PortfolioHoldingResponse;
import com.wealthmanager.backend.service.CleanLedgerService;
import com.wealthmanager.backend.service.PortfolioService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tools for LLMs to fetch user portfolio and clean ledger:
 * stocks and mutual funds owned, derived from Gmail/SMS and stored as structured data.
 */
@Component
public class PortfolioMcpTools {

    private final PortfolioService portfolioService;
    private final CleanLedgerService cleanLedgerService;

    public PortfolioMcpTools(PortfolioService portfolioService, CleanLedgerService cleanLedgerService) {
        this.portfolioService = portfolioService;
        this.cleanLedgerService = cleanLedgerService;
    }

    /**
     * Returns all portfolio holdings (stocks and mutual funds) owned by the user.
     */
    @McpTool(
            name = "get_portfolio_holdings",
            description = "Fetch all stocks and mutual funds owned by the user. Data is derived from Gmail/SMS messages and stored as structured portfolio state. Returns symbol, name, quantity, average price, current value (if available), instrument type (STOCK or MUTUAL_FUND)."
    )
    public List<Map<String, Object>> getPortfolioHoldings() {
        return portfolioService.getAllHoldings()
                .stream()
                .map(this::holdingToMap)
                .collect(Collectors.toList());
    }

    /**
     * Returns only stock holdings.
     */
    @McpTool(
            name = "get_stock_holdings",
            description = "Fetch stocks owned by the user, derived from Gmail/SMS and stored in the portfolio. Returns symbol, name, quantity, average price, current value."
    )
    public List<Map<String, Object>> getStockHoldings() {
        return portfolioService.getStockHoldings()
                .stream()
                .map(this::holdingToMap)
                .collect(Collectors.toList());
    }

    /**
     * Returns only mutual fund holdings.
     */
    @McpTool(
            name = "get_mutual_fund_holdings",
            description = "Fetch mutual funds owned by the user, derived from Gmail/SMS and stored in the portfolio. Returns symbol/folio, name, units, average price, current value."
    )
    public List<Map<String, Object>> getMutualFundHoldings() {
        return portfolioService.getMutualFundHoldings()
                .stream()
                .map(this::holdingToMap)
                .collect(Collectors.toList());
    }

    /**
     * Returns portfolio summary (counts and total value).
     */
    @McpTool(
            name = "get_portfolio_summary",
            description = "Fetch summary of user portfolio: total holdings count, stock count, mutual fund count, and total current value (when available). Data comes from Gmail/SMS-derived structured portfolio state."
    )
    public Map<String, Object> getPortfolioSummary() {
        return new LinkedHashMap<>(portfolioService.getPortfolioSummary());
    }

    /**
     * Returns the clean ledger: structured entries (buy, sell, SIP, dividend, etc.) parsed from Gmail/SMS.
     */
    @McpTool(
            name = "get_clean_ledger",
            description = "Fetch the clean ledger: structured ledger entries (e.g. BUY, SELL, SIP, REDEMPTION, DIVIDEND) for stocks and mutual funds, parsed from Gmail/SMS. Optional instrumentType filter: STOCK or MUTUAL_FUND. Returns up to 50 entries, most recent first."
    )
    public List<Map<String, Object>> getCleanLedger(
            @McpToolParam(description = "Optional: STOCK or MUTUAL_FUND to filter by instrument type; omit for all", required = false) String instrumentType) {

        List<CleanLedgerEntryResponse> entries;
        if (instrumentType != null && !instrumentType.isBlank()) {
            String type = "MUTUAL_FUND".equalsIgnoreCase(instrumentType) ? PortfolioService.INSTRUMENT_MUTUAL_FUND : PortfolioService.INSTRUMENT_STOCK;
            entries = cleanLedgerService.getEntriesByInstrumentType(type, PageRequest.of(0, 50));
        } else {
            entries = cleanLedgerService.getAllEntries(PageRequest.of(0, 50)).getContent();
        }
        return entries.stream().map(this::ledgerEntryToMap).collect(Collectors.toList());
    }

    private Map<String, Object> holdingToMap(PortfolioHoldingResponse h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.id());
        m.put("instrumentType", h.instrumentType());
        m.put("symbol", h.symbol() != null ? h.symbol() : "");
        m.put("name", h.name() != null ? h.name() : "");
        m.put("quantity", h.quantity());
        m.put("averagePrice", h.averagePrice());
        m.put("currentValue", h.currentValue());
        m.put("currency", h.currency());
        m.put("lastUpdated", h.lastUpdated().toString());
        return m;
    }

    private Map<String, Object> ledgerEntryToMap(CleanLedgerEntryResponse e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("entryType", e.entryType());
        m.put("instrumentType", e.instrumentType());
        m.put("symbol", e.symbol() != null ? e.symbol() : "");
        m.put("name", e.name() != null ? e.name() : "");
        m.put("quantity", e.quantity());
        m.put("price", e.price());
        m.put("amount", e.amount());
        m.put("currency", e.currency());
        m.put("ledgerDate", e.ledgerDate().toString());
        m.put("description", e.description() != null ? e.description() : "");
        return m;
    }
}
