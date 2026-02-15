package com.wealthmanager.backend.repository;

import com.wealthmanager.backend.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Used for deduplication: same txn from SMS and email should not be stored twice. */
    boolean existsByDedupeKeyAndTransactionDateBetween(String dedupeKey, LocalDateTime start, LocalDateTime end);

    Page<Transaction> findAllByOrderByTransactionDateDesc(Pageable pageable);

    List<Transaction> findByCategory(String category);

    @Query("SELECT COUNT(t) FROM Transaction t")
    long countAll();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = 'DEBIT'")
    BigDecimal sumDebitAmount();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = 'CREDIT'")
    BigDecimal sumCreditAmount();
}
