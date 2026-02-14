package com.wealthmanager.backend.controller;

import com.wealthmanager.backend.model.dto.TransactionResponse;
import com.wealthmanager.backend.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("Fetching transactions page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<TransactionResponse> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable Long id) {
        log.debug("Fetching transaction id={}", id);
        TransactionResponse transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTransactionSummary() {
        log.debug("Fetching transaction summary");
        Map<String, Object> summary = transactionService.getTransactionSummary();
        return ResponseEntity.ok(summary);
    }
}
