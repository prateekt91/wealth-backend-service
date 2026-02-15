package com.wealthmanager.backend.service;

import com.wealthmanager.backend.ai.TransactionParser;
import com.wealthmanager.backend.model.RawIngestion;
import com.wealthmanager.backend.model.Transaction;
import com.wealthmanager.backend.model.dto.TransactionParseResult;
import com.wealthmanager.backend.repository.RawIngestionRepository;
import com.wealthmanager.backend.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Processes raw ingestions through the AI parser and persists transactions.
 * Can be triggered after each ingestion (async) or via scheduler for backlog.
 */
@Service
@Slf4j
public class TransactionParsingService {

    private final TransactionParser transactionParser;
    private final RawIngestionRepository rawIngestionRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public TransactionParsingService(TransactionParser transactionParser,
                                    RawIngestionRepository rawIngestionRepository,
                                    TransactionRepository transactionRepository,
                                    NotificationService notificationService) {
        this.transactionParser = transactionParser;
        this.rawIngestionRepository = rawIngestionRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    /**
     * Process a single raw ingestion: parse with LLM, save transaction if parsed, mark ingestion processed.
     */
    @Transactional
    public boolean processOne(RawIngestion ingestion) {
        if (Boolean.TRUE.equals(ingestion.getProcessed())) {
            return false;
        }

        Optional<TransactionParseResult> parsed = transactionParser.parse(ingestion.getRawBody());
        if (parsed.isEmpty()) {
            log.debug("No transaction parsed from ingestion id={}", ingestion.getId());
            return false;
        }

        TransactionParseResult p = parsed.get();
        LocalDateTime txnDate = p.transactionDate() != null ? p.transactionDate() : ingestion.getReceivedAt();
        String currency = p.currency() != null && !p.currency().isBlank() ? p.currency() : "INR";

        String dedupeKey = computeDedupeKey(p.amount(), currency, p.transactionType(), txnDate, p.merchantName());
        LocalDateTime windowStart = txnDate.minusDays(7);
        LocalDateTime windowEnd = txnDate.plusDays(1);
        if (transactionRepository.existsByDedupeKeyAndTransactionDateBetween(dedupeKey, windowStart, windowEnd)) {
            ingestion.setProcessed(true);
            ingestion.setProcessedAt(LocalDateTime.now());
            rawIngestionRepository.save(ingestion);
            log.info("Skipped duplicate transaction (SMS/email same txn) for ingestion id={}, dedupeKey={}", ingestion.getId(), dedupeKey);
            return true;
        }

        Transaction txn = Transaction.builder()
                .rawIngestionId(ingestion.getId())
                .amount(p.amount())
                .currency(currency)
                .merchantName(p.merchantName())
                .category(p.category())
                .transactionType(p.transactionType())
                .transactionDate(txnDate)
                .description(p.description())
                .dedupeKey(dedupeKey)
                .build();

        transactionRepository.save(txn);
        ingestion.setProcessed(true);
        ingestion.setProcessedAt(LocalDateTime.now());
        rawIngestionRepository.save(ingestion);

        notificationService.notifyNewTransaction(txn);
        log.info("Parsed and saved transaction id={} from ingestion id={}", txn.getId(), ingestion.getId());
        return true;
    }

    /**
     * Fingerprint for deduplication: same amount, type, date (day), and merchant = same transaction
     * (e.g. SMS and email for one payment should not create two rows).
     */
    private String computeDedupeKey(java.math.BigDecimal amount, String currency, String transactionType,
                                    LocalDateTime transactionDate, String merchantName) {
        String amountStr = amount != null ? amount.stripTrailingZeros().toPlainString() : "";
        String type = transactionType != null ? transactionType.toUpperCase() : "";
        String dateStr = transactionDate != null ? transactionDate.toLocalDate().toString() : "";
        String merchant = merchantName != null
                ? merchantName.trim().toUpperCase().replaceAll("\\s+", " ")
                : "";
        String payload = amountStr + "|" + currency + "|" + type + "|" + dateStr + "|" + merchant;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Triggered asynchronously after new ingestion (SMS or email).
     */
    @Async
    public void processAsync(Long ingestionId) {
        try {
            rawIngestionRepository.findById(ingestionId).ifPresent(this::processOne);
        } catch (Exception e) {
            log.warn("Async parse failed for ingestion id={}: {}", ingestionId, e.getMessage());
        }
    }

    /**
     * Process all unprocessed ingestions (e.g. from scheduler or on startup).
     */
    @Transactional
    public int processBacklog() {
        List<RawIngestion> unprocessed = rawIngestionRepository.findByProcessedFalse();
        if (unprocessed.isEmpty()) {
            log.debug("Backlog: no unprocessed ingestions");
            return 0;
        }
        log.info("Backlog: processing {} unprocessed ingestion(s)", unprocessed.size());
        int processed = 0;
        for (RawIngestion ingestion : unprocessed) {
            try {
                if (processOne(ingestion)) {
                    processed++;
                } else {
                    log.info("Backlog: no transaction extracted from ingestion id={} (source={}, sourceId={})",
                            ingestion.getId(), ingestion.getSource(), ingestion.getSourceId());
                }
            } catch (Exception e) {
                log.warn("Parse failed for ingestion id={}: {}", ingestion.getId(), e.getMessage(), e);
            }
        }
        if (processed > 0) {
            log.info("Processed {} ingestions from backlog", processed);
        } else {
            log.warn("Backlog: 0 transactions saved from {} ingestion(s). Check LLM/parser logs above.", unprocessed.size());
        }
        return processed;
    }

    /** Process unprocessed ingestions every 5 minutes. */
    @Scheduled(fixedDelayString = "${app.ai.backlog-interval-ms:300000}")
    public void processBacklogScheduled() {
        processBacklog();
    }
}
