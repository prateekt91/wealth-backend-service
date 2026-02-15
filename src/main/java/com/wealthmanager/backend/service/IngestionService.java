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

    public IngestionService(RawIngestionRepository rawIngestionRepository,
                            NotificationService notificationService,
                            TransactionParsingService transactionParsingService) {
        this.rawIngestionRepository = rawIngestionRepository;
        this.notificationService = notificationService;
        this.transactionParsingService = transactionParsingService;
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
                .build();

        RawIngestion saved = rawIngestionRepository.save(ingestion);
        log.info("Saved raw ingestion id={}", saved.getId());

        notificationService.notifyNewIngestion(saved);
        transactionParsingService.processAsync(saved.getId());
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
        log.info("Ingesting email from sender={}, gmailId={}", sender, gmailMessageId);

        RawIngestion ingestion = RawIngestion.builder()
                .source("EMAIL")
                .sourceId(gmailMessageId)
                .senderAddress(sender)
                .rawBody(body)
                .receivedAt(receivedAt != null ? receivedAt : LocalDateTime.now())
                .processed(false)
                .build();

        RawIngestion saved = rawIngestionRepository.save(ingestion);
        log.info("Saved email ingestion id={}, gmailId={}", saved.getId(), gmailMessageId);

        notificationService.notifyNewIngestion(saved);
        transactionParsingService.processAsync(saved.getId());
        return saved;
    }

    /**
     * Check if a message with the given sourceId has already been ingested.
     */
    public boolean isAlreadyIngested(String sourceId) {
        return rawIngestionRepository.existsBySourceId(sourceId);
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
