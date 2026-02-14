package com.wealthmanager.backend.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.wealthmanager.backend.config.GmailClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.gmail", name = "enabled", havingValue = "true")
public class GmailPollerService {

    private static final String USER_ME = "me";

    private final Gmail gmail;
    private final GmailClientConfig gmailConfig;
    private final IngestionService ingestionService;
    private final AtomicLong lastPollEpochSeconds;

    public GmailPollerService(Gmail gmail,
                              GmailClientConfig gmailConfig,
                              IngestionService ingestionService) {
        this.gmail = gmail;
        this.gmailConfig = gmailConfig;
        this.ingestionService = ingestionService;

        // Initialize: look back from configured minutes ago
        long lookbackSeconds = (long) gmailConfig.getInitialLookbackMinutes() * 60;
        this.lastPollEpochSeconds = new AtomicLong(Instant.now().getEpochSecond() - lookbackSeconds);

        log.info("Gmail poller initialized. Looking back {} minutes from now.",
                gmailConfig.getInitialLookbackMinutes());
    }

    @Scheduled(fixedDelayString = "${app.gmail.poll-interval-ms:60000}")
    public void pollGmail() {
        if (gmail == null) {
            log.warn("Gmail client not available. Skipping poll.");
            return;
        }

        long pollStartEpoch = Instant.now().getEpochSecond();
        long afterEpoch = lastPollEpochSeconds.get();

        try {
            String query = buildSearchQuery(afterEpoch);
            log.debug("Polling Gmail with query: {}", query);

            List<Message> messages = fetchMessages(query);

            if (messages.isEmpty()) {
                log.debug("No new transaction emails found.");
            } else {
                log.info("Found {} candidate emails from Gmail.", messages.size());
                processMessages(messages);
            }

            // Move the window forward (with 60s overlap for safety)
            lastPollEpochSeconds.set(pollStartEpoch - 60);

        } catch (Exception e) {
            log.error("Error polling Gmail: {}", e.getMessage(), e);
        }
    }

    private String buildSearchQuery(long afterEpochSeconds) {
        String[] keywords = gmailConfig.getSearchKeywords().split(",");
        String keywordQuery = Arrays.stream(keywords)
                .map(String::trim)
                .filter(k -> !k.isBlank())
                .map(k -> "\"" + k + "\"")
                .collect(Collectors.joining(" OR "));

        // Gmail search supports `after:` with epoch seconds
        return String.format("after:%d (%s)", afterEpochSeconds, keywordQuery);
    }

    private List<Message> fetchMessages(String query) throws Exception {
        ListMessagesResponse response = gmail.users().messages()
                .list(USER_ME)
                .setQ(query)
                .setLabelIds(List.of("INBOX"))
                .setMaxResults(50L)
                .execute();

        List<Message> messages = response.getMessages();
        return messages != null ? messages : Collections.emptyList();
    }

    private void processMessages(List<Message> messages) {
        for (Message messageSummary : messages) {
            try {
                String gmailMessageId = messageSummary.getId();

                // Deduplicate: skip if already ingested
                if (ingestionService.isAlreadyIngested(gmailMessageId)) {
                    log.debug("Skipping already-ingested Gmail message id={}", gmailMessageId);
                    continue;
                }

                // Fetch full message
                Message fullMessage = gmail.users().messages()
                        .get(USER_ME, gmailMessageId)
                        .setFormat("full")
                        .execute();

                String sender = extractHeader(fullMessage, "From");
                String subject = extractHeader(fullMessage, "Subject");
                String body = extractBody(fullMessage);
                long internalDateMs = fullMessage.getInternalDate();

                LocalDateTime receivedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(internalDateMs),
                        ZoneId.systemDefault()
                );

                // Build composite body: subject + body for AI parsing later
                String compositeBody = buildCompositeBody(subject, body);

                ingestionService.ingestEmail(gmailMessageId, sender, compositeBody, receivedAt);
                log.info("Ingested Gmail email id={}, subject={}", gmailMessageId, subject);

            } catch (Exception e) {
                log.error("Error processing Gmail message id={}: {}",
                        messageSummary.getId(), e.getMessage(), e);
            }
        }
    }

    private String extractHeader(Message message, String headerName) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return "";
        }
        return message.getPayload().getHeaders().stream()
                .filter(h -> headerName.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    private String extractBody(Message message) {
        MessagePart payload = message.getPayload();
        if (payload == null) {
            return "";
        }

        // Try direct body (simple messages)
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            return decodeBase64Url(payload.getBody().getData());
        }

        // Try multipart: look for text/plain first, then text/html
        if (payload.getParts() != null) {
            String textBody = extractPartByMimeType(payload.getParts(), "text/plain");
            if (!textBody.isBlank()) {
                return textBody;
            }
            String htmlBody = extractPartByMimeType(payload.getParts(), "text/html");
            if (!htmlBody.isBlank()) {
                return stripHtmlTags(htmlBody);
            }
        }

        return "";
    }

    private String extractPartByMimeType(List<MessagePart> parts, String mimeType) {
        for (MessagePart part : parts) {
            if (mimeType.equals(part.getMimeType())
                    && part.getBody() != null
                    && part.getBody().getData() != null) {
                return decodeBase64Url(part.getBody().getData());
            }
            // Recurse into nested parts
            if (part.getParts() != null) {
                String result = extractPartByMimeType(part.getParts(), mimeType);
                if (!result.isBlank()) {
                    return result;
                }
            }
        }
        return "";
    }

    private String decodeBase64Url(String data) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(data);
            return new String(bytes);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode base64url email body: {}", e.getMessage());
            return "";
        }
    }

    private String stripHtmlTags(String html) {
        // Basic HTML tag stripping for plain-text extraction
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildCompositeBody(String subject, String body) {
        StringBuilder sb = new StringBuilder();
        if (subject != null && !subject.isBlank()) {
            sb.append("[Subject: ").append(subject).append("] ");
        }
        if (body != null && !body.isBlank()) {
            sb.append(body);
        }
        return sb.toString().trim();
    }
}
