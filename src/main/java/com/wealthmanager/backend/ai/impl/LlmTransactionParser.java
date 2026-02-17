package com.wealthmanager.backend.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Iterator;
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
        You are a financial transaction parser. Your ONLY job is to extract transaction details from SMS/email messages.
        
        CRITICAL RULES:
        1. You MUST respond with ONLY a JSON object matching the exact schema provided below.
        2. Do NOT include any other text, explanations, markdown, CSS, code blocks, or formatting.
        3. Your response must start with { and end with }.
        4. If the message is NOT a transaction (e.g. OTP, promotional message, general information), return: {"transactionType":"NONE","amount":0}
        5. Do NOT answer questions, provide explanations, or return information unrelated to transactions.
        6. Do NOT return nested objects or arrays - only flat JSON with the fields: amount, currency, merchantName, category, transactionType, transactionDate, description
        
        Transaction fields:
        - amount: positive number (required)
        - currency: string, default "INR" (optional)
        - merchantName: string (optional)
        - category: string (optional)
        - transactionType: "DEBIT" or "CREDIT" (required)
        - transactionDate: ISO-8601 format like "2026-02-14T10:30:00" (optional)
        - description: string (optional)
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
            Parse this financial message and extract transaction details.
            
            IMPORTANT: Respond with ONLY a JSON object. No markdown, no code blocks, no CSS, no explanations.
            Start your response with { and end with }.
            
            Message to parse:
            %s
            
            JSON only:
            """, rawText.trim());

        try {
            log.info("Calling LLM for message length={}. First 200 chars: {}",
                    rawText.length(), rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText);
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

            // Validate that extracted text is actually JSON before attempting to parse
            if (!isValidJson(jsonOnly)) {
                log.warn("LLM response extracted text is not valid JSON (length={}). First 200 chars: {}",
                        jsonOnly.length(), jsonOnly.length() > 200 ? jsonOnly.substring(0, 200) + "..." : jsonOnly);
                log.warn("Full LLM response (first 500 chars): {}",
                        content.length() > 500 ? content.substring(0, 500) + "..." : content);
                return Optional.empty();
            }

            // Validate that JSON matches the expected schema before attempting conversion
            if (!matchesTransactionSchema(jsonOnly)) {
                log.warn("LLM response does not match TransactionParseResult schema (length={}). First 300 chars: {}",
                        jsonOnly.length(), jsonOnly.length() > 300 ? jsonOnly.substring(0, 300) + "..." : jsonOnly);
                log.warn("Full LLM response (first 500 chars): {}",
                        content.length() > 500 ? content.substring(0, 500) + "..." : content);
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

    /**
     * Validates that the extracted text is actually valid JSON (not CSS or other text).
     * Checks for JSON-like structure: starts with {, contains quoted keys, etc.
     */
    private boolean isValidJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        // Check for JSON-like patterns: quoted keys, colons, etc.
        // CSS would have properties like "font-family:" without quotes around the key
        // JSON must have quoted keys like "key":
        if (!trimmed.contains("\"")) {
            // JSON should have at least one quoted string (key or value)
            return false;
        }
        // Quick validation: try to parse with ObjectMapper to see if it's valid JSON
        try {
            objectMapper.readTree(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that the JSON matches the TransactionParseResult schema.
     * Checks for required fields: amount, transactionType.
     * Also checks that it's a flat object (not nested structures like the Visa/Mastercard example).
     */
    private boolean matchesTransactionSchema(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            if (!root.isObject()) {
                return false;
            }
            
            // Check for required fields
            if (!root.has("amount") || !root.has("transactionType")) {
                return false;
            }
            
            // Check that amount is a number
            JsonNode amountNode = root.get("amount");
            if (!amountNode.isNumber() && !amountNode.isTextual()) {
                // Allow textual numbers (will be parsed later)
                return false;
            }
            
            // Check that transactionType is a string
            JsonNode typeNode = root.get("transactionType");
            if (!typeNode.isTextual()) {
                return false;
            }
            
            // Reject nested objects that don't belong to transaction schema
            // TransactionParseResult fields: amount, currency, merchantName, category, transactionType, transactionDate, description
            // All should be primitives or null, not nested objects
            Iterator<String> fieldNamesIterator = root.fieldNames();
            while (fieldNamesIterator.hasNext()) {
                String fieldName = fieldNamesIterator.next();
                JsonNode fieldValue = root.get(fieldName);
                // If it's a nested object or array (and not one of the expected simple fields), reject it
                if ((fieldValue.isObject() || fieldValue.isArray()) && !isExpectedComplexField(fieldName)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.debug("Schema validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a field name is expected to have a complex (object/array) value.
     * For TransactionParseResult, all fields are primitives/null, so this returns false.
     */
    private boolean isExpectedComplexField(String fieldName) {
        // TransactionParseResult has no complex fields - all are primitives
        return false;
    }
}
