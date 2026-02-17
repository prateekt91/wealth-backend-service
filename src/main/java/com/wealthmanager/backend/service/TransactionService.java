package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.Transaction;
import com.wealthmanager.backend.model.dto.TransactionResponse;
import com.wealthmanager.backend.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAllByOrderByTransactionDateDesc(pageable)
                .map(this::toResponse);
    }

    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
        return toResponse(transaction);
    }

    public List<TransactionResponse> getTransactionsByType(String transactionType, Pageable pageable) {
        return transactionRepository.findByTransactionTypeOrderByTransactionDateDesc(transactionType, pageable)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getTransactionSummary() {
        long totalCount = transactionRepository.countAll();
        BigDecimal totalDebit = transactionRepository.sumDebitAmount();
        BigDecimal totalCredit = transactionRepository.sumCreditAmount();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", totalCount);
        summary.put("totalDebit", totalDebit);
        summary.put("totalCredit", totalCredit);
        summary.put("netAmount", totalCredit.subtract(totalDebit));

        return summary;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantName(),
                transaction.getCategory(),
                transaction.getTransactionType(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
