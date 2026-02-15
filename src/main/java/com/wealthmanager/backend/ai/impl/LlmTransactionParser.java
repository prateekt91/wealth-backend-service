package com.wealthmanager.backend.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthmanager.backend.ai.TransactionParser;
import com.wealthmanager.backend.model.dto.TransactionParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Transaction parser using a chat model (Ollama locally; OpenAI/Azure in future).
 * Uses structured output to get JSON matching {@link TransactionParseResult}.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
@Slf4j
public class LlmTransactionParser implements TransactionParser {

    private static final String SYSTEM_INSTRUCTIONS = """
        You are a financial message parser. Extract exactly one bank/UPI transaction from the given message.
        Return ONLY valid JSON matching the required schema. If the message is not a transaction (e.g. OTP, promo), return {"transactionType":"NONE","amount":0}.
        Use transactionType "DEBIT" or "CREDIT". Use ISO-8601 for transactionDate (e.g. 2026-02-14T10:30:00).
        Amount must be a positive number. Currency default is INR.
        """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<TransactionParseResult> outputConverter;
    private final ObjectMapper objectMapper;

    public LlmTransactionParser(
            @Qualifier("ollamaChatModel") ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.outputConverter = new BeanOutputConverter<>(TransactionParseResult.class, objectMapper);
    }

    @Override
    public Optional<TransactionParseResult> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        String formatInstructions = outputConverter.getFormat();
        String systemMessage = SYSTEM_INSTRUCTIONS + "\n\n" + formatInstructions;
        String userMessage = String.format("""
            Parse this message. Respond with only a single JSON object (no other text, no markdown):

            ---
            %s
            ---
            """, rawText.trim());

        try {
            log.info("Calling LLM for message length={}", rawText.length());
            var prompt = new Prompt(List.of(
                    new org.springframework.ai.chat.messages.SystemMessage(systemMessage),
                    new org.springframework.ai.chat.messages.UserMessage(userMessage)
            ));
            var response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                log.info("LLM returned empty response for message (length={})", rawText.length());
                return Optional.empty();
            }

            String jsonOnly = extractJsonFromResponse(content);
            if (jsonOnly.isBlank()) {
                log.warn("LLM response contained no JSON object (content length={}). First 200 chars: {}",
                        content.length(), content.length() > 200 ? content.substring(0, 200) + "..." : content);
                return Optional.empty();
            }

            TransactionParseResult result = outputConverter.convert(jsonOnly);
            if (result == null || "NONE".equalsIgnoreCase(result.transactionType())) {
                log.info("LLM parsed as non-transaction (transactionType=NONE) for message length={}", rawText.length());
                return Optional.empty();
            }
            if (result.amount() == null || result.amount().signum() <= 0) {
                log.info("LLM returned invalid amount for message length={}", rawText.length());
                return Optional.empty();
            }

            return Optional.of(result);
        } catch (Exception e) {
            log.warn("LLM parse failed for message (length={}): {}", rawText.length(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Extracts the JSON object from LLM response, which may include echoed instructions,
     * schema text, or markdown code blocks. Returns only the first top-level {...} segment.
     */
    private String extractJsonFromResponse(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String s = content.trim();
        // Remove markdown code fences (```json ... ``` or ``` ... ```)
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start != -1) {
                s = s.substring(start + 1).trim();
            }
            int end = s.lastIndexOf("```");
            if (end != -1) {
                s = s.substring(0, end).trim();
            }
        }
        int firstBrace = s.indexOf('{');
        if (firstBrace == -1) {
            return "";
        }
        int depth = 0;
        for (int i = firstBrace; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(firstBrace, i + 1);
                }
            }
        }
        return s.substring(firstBrace);
    }
}
