package com.wealthmanager.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Provides information about the MCP server endpoints.
 * The actual MCP SSE endpoint is at /mcp/message (handled by Spring AI MCP server).
 */
@RestController
@RequestMapping("/mcp")
public class McpInfoController {

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getMcpInfo() {
        return ResponseEntity.ok(Map.of(
                "name", "wealth-manager-mcp",
                "version", "1.0.0",
                "protocol", "SSE",
                "endpoint", "/mcp/message",
                "description", "MCP server for Wealth Manager. Connect to /mcp/message for SSE transport.",
                "tools", Map.of(
                        "transaction", new String[]{"get_transaction_type", "list_transactions_by_type"},
                        "portfolio", new String[]{
                                "get_portfolio_holdings",
                                "get_stock_holdings",
                                "get_mutual_fund_holdings",
                                "get_portfolio_summary",
                                "get_clean_ledger"
                        }
                )
        ));
    }
}
