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

    public IngestionService(RawIngestionRepository rawIngestionRepository,
                            NotificationService notificationService) {
        this.rawIngestionRepository = rawIngestionRepository;
        this.notificationService = notificationService;
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

        // TODO: Trigger AI/MCP parsing pipeline asynchronously

        return saved;
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
