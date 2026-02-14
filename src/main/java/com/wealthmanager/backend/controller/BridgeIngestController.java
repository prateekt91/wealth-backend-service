package com.wealthmanager.backend.controller;

import com.wealthmanager.backend.model.RawIngestion;
import com.wealthmanager.backend.model.dto.SmsPayload;
import com.wealthmanager.backend.service.IngestionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bridge")
@Slf4j
public class BridgeIngestController {

    private final IngestionService ingestionService;

    public BridgeIngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestSms(@Valid @RequestBody SmsPayload payload) {
        log.info("Received SMS ingestion request from sender={}", payload.sender());

        RawIngestion ingestion = ingestionService.ingestSms(payload);

        Map<String, Object> response = Map.of(
                "status", "accepted",
                "ingestionId", ingestion.getId(),
                "message", "SMS queued for processing"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
