package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.RawIngestion;
import com.wealthmanager.backend.model.dto.SmsPayload;
import com.wealthmanager.backend.repository.RawIngestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@Slf4j
public class IngestionService {

    private final RawIngestionRepository rawIngestionRepository;
    private final NotificationService notificationService;
    private final TransactionParsingService transactionParsingService;
    private final HoldingsParsingService holdingsParsingService;

    public IngestionService(RawIngestionRepository rawIngestionRepository,
                            NotificationService notificationService,
                            TransactionParsingService transactionParsingService,
                            HoldingsParsingService holdingsParsingService) {
        this.rawIngestionRepository = rawIngestionRepository;
        this.notificationService = notificationService;
        this.transactionParsingService = transactionParsingService;
        this.holdingsParsingService = holdingsParsingService;
    }

    @Transactional
    public RawIngestion ingestSms(SmsPayload payload) {
        log.info("Ingesting SMS from sender={}", payload.sender());

        LocalDateTime receivedAt = parseReceivedAt(payload.receivedAt());

        RawIngestion ingestion = RawIngestion.builder()
                .source("SMS")
                .senderAddress(payload.sender())
                .rawBody(payload.body())
                .receivedAt(receivedAt)
                .processed(false)
                .ingested(false)
                .build();

        RawIngestion saved = rawIngestionRepository.save(ingestion);
        
        // Mark as successfully ingested
        saved.setIngested(true);
        saved = rawIngestionRepository.save(saved);
        log.info("Saved raw ingestion id={}, ingested=true", saved.getId());

        notificationService.notifyNewIngestion(saved);
        transactionParsingService.processAsync(saved.getId());
        holdingsParsingService.processAsync(saved.getId());
        return saved;
    }

    /**
     * Ingest an email from Gmail polling.
     *
     * @param gmailMessageId Gmail message ID (used as sourceId for deduplication)
     * @param sender         email sender (From header)
     * @param body           composite body (subject + email body text)
     * @param receivedAt     email received timestamp
     * @return the saved RawIngestion entity
     */
    @Transactional
    public RawIngestion ingestEmail(String gmailMessageId, String sender,
                                    String body, LocalDateTime receivedAt) {
        // Check if already ingested using the ingested indicator
        if (isAlreadyIngested(gmailMessageId)) {
            log.debug("Skipping already ingested email gmailId={}", gmailMessageId);
            return null;
        }

        log.info("Ingesting email from sender={}, gmailId={}", sender, gmailMessageId);

        RawIngestion ingestion = RawIngestion.builder()
                .source("EMAIL")
                .sourceId(gmailMessageId)
                .senderAddress(sender)
                .rawBody(body)
                .receivedAt(receivedAt != null ? receivedAt : LocalDateTime.now())
                .processed(false)
                .ingested(false)
                .build();

        RawIngestion saved = rawIngestionRepository.save(ingestion);
        
        // Mark as successfully ingested
        saved.setIngested(true);
        saved = rawIngestionRepository.save(saved);
        log.info("Saved email ingestion id={}, gmailId={}, ingested=true", saved.getId(), gmailMessageId);

        notificationService.notifyNewIngestion(saved);
        transactionParsingService.processAsync(saved.getId());
        holdingsParsingService.processAsync(saved.getId());
        return saved;
    }

    /**
     * Check if a message with the given sourceId has already been ingested.
     * Uses the ingested indicator to determine if the record was successfully stored.
     */
    public boolean isAlreadyIngested(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }
        return rawIngestionRepository.existsBySourceIdAndIngestedTrue(sourceId);
    }

    /**
     * Validates if email content is fit for ingestion (contains financial transaction/holding data).
     * Returns true if the email should be ingested, false if it should be skipped.
     */
    public boolean isFitForIngestion(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        
        String lowerBody = body.toLowerCase();
        
        // Must contain financial keywords
        String[] requiredKeywords = {
                "debit", "credit", "debited", "credited", "transaction", "payment",
                "transferred", "upi", "neft", "imps", "withdrawn", "deposited",
                "purchased", "bought", "sip", "redeemed", "sold", "units", "shares",
                "mutual fund", "mf", "nav", "stock", "equity", "investment"
        };
        
        boolean hasFinancialKeyword = false;
        for (String keyword : requiredKeywords) {
            if (lowerBody.contains(keyword)) {
                hasFinancialKeyword = true;
                break;
            }
        }
        
        if (!hasFinancialKeyword) {
            return false;
        }
        
        // Exclude promotional/marketing emails
        String[] excludePatterns = {
                "unsubscribe", "marketing", "promotion", "offer", "discount",
                "newsletter", "subscribe", "click here", "limited time"
        };
        
        for (String pattern : excludePatterns) {
            if (lowerBody.contains(pattern)) {
                // If it's clearly promotional, skip even if it has financial keywords
                return false;
            }
        }
        
        // Must contain numbers (amounts, quantities, etc.)
        boolean hasNumbers = body.matches(".*\\d+.*");
        
        return hasNumbers;
    }

    /**
     * Mark an email as skipped (not fit for ingestion) to prevent re-ingestion.
     * Saves a minimal record with sourceId, processed=true, and ingested=true.
     */
    @Transactional
    public void markAsSkipped(String gmailMessageId, String sender, LocalDateTime receivedAt) {
        if (gmailMessageId == null || gmailMessageId.isBlank()) {
            return;
        }
        
        // Check if already ingested using the ingested indicator
        if (isAlreadyIngested(gmailMessageId)) {
            return;
        }
        
        RawIngestion skipped = RawIngestion.builder()
                .source("EMAIL")
                .sourceId(gmailMessageId)
                .senderAddress(sender)
                .rawBody("[SKIPPED - Not fit for ingestion]")
                .receivedAt(receivedAt != null ? receivedAt : LocalDateTime.now())
                .processed(true) // Mark as processed so it won't be parsed
                .processedAt(LocalDateTime.now())
                .ingested(true) // Mark as ingested to prevent re-ingestion attempts
                .build();
        
        rawIngestionRepository.save(skipped);
        log.debug("Marked email gmailId={} as skipped (not fit for ingestion), ingested=true", gmailMessageId);
    }

    private LocalDateTime parseReceivedAt(String receivedAt) {
        if (receivedAt == null || receivedAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(receivedAt, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse receivedAt='{}', using current time", receivedAt);
            return LocalDateTime.now();
        }
    }
}
