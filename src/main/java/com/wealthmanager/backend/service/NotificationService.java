package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.RawIngestion;
import com.wealthmanager.backend.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyNewIngestion(RawIngestion ingestion) {
        log.info("Sending WebSocket notification for new ingestion id={}", ingestion.getId());
        messagingTemplate.convertAndSend("/topic/ingestion", ingestion);
    }

    public void notifyNewTransaction(Transaction transaction) {
        log.info("Sending WebSocket notification for new transaction id={}", transaction.getId());
        messagingTemplate.convertAndSend("/topic/transactions", transaction);
    }
}
