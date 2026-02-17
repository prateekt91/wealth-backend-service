package com.wealthmanager.backend.mcp;

import com.wealthmanager.backend.model.dto.TransactionResponse;
import com.wealthmanager.backend.service.TransactionService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tools for LLMs to discover what kind of transaction a stored transaction is:
 * DEBIT (money out) or CREDIT (money in).
 */
@Component
public class TransactionMcpTools {

    private static final String TYPE_CREDIT = "CREDIT";
    private static final String TYPE_DEBIT = "DEBIT";

    private final TransactionService transactionService;

    public TransactionMcpTools(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Returns the transaction type (DEBIT or CREDIT) for a transaction by ID.
     * CREDIT = money added to the account. DEBIT = money taken out.
     */
    @McpTool(
            name = "get_transaction_type",
            description = "Discover what kind of transaction a stored transaction is: DEBIT (money out) or CREDIT (money in). Returns the transaction type and details for the given transaction ID."
    )
    public Map<String, Object> getTransactionType(
            @McpToolParam(description = "The unique ID of the transaction", required = true) Long transactionId) {

        TransactionResponse t = transactionService.getTransactionById(transactionId);
        String type = t.transactionType();
        String kind = (TYPE_CREDIT.equalsIgnoreCase(type))
                ? "CREDIT (money in)"
                : (TYPE_DEBIT.equalsIgnoreCase(type))
                ? "DEBIT (money out)"
                : type;

        return Map.of(
                "transactionId", transactionId,
                "transactionType", type,
                "kind", kind,
                "amount", t.amount(),
                "currency", t.currency(),
                "merchantName", t.merchantName() != null ? t.merchantName() : "",
                "description", t.description() != null ? t.description() : ""
        );
    }

    /**
     * Lists transactions of a given type (DEBIT or CREDIT).
     */
    @McpTool(
            name = "list_transactions_by_type",
            description = "List transactions that are either CREDIT (money in) or DEBIT (money out). Use type 'CREDIT' or 'DEBIT'. Returns up to 50 transactions of that type, most recent first."
    )
    public List<Map<String, Object>> listTransactionsByType(
            @McpToolParam(description = "Transaction type: CREDIT or DEBIT", required = true) String type) {

        String normalized = TYPE_CREDIT.equalsIgnoreCase(type) ? TYPE_CREDIT : TYPE_DEBIT;
        return transactionService.getTransactionsByType(normalized, PageRequest.of(0, 50))
                .stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.id(),
                        "transactionType", t.transactionType(),
                        "kind", TYPE_CREDIT.equalsIgnoreCase(t.transactionType()) ? "CREDIT (money in)" : "DEBIT (money out)",
                        "amount", t.amount(),
                        "merchantName", t.merchantName() != null ? t.merchantName() : "",
                        "transactionDate", t.transactionDate().toString()
                ))
                .collect(Collectors.toList());
    }
}
