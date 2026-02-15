package com.wealthmanager.backend.ai;

import com.wealthmanager.backend.model.dto.TransactionParseResult;

import java.util.Optional;

/**
 * Parses raw message text (SMS/email) into a structured transaction.
 * Implementations can use local LLM (Ollama) or public LLM (OpenAI, Azure) in the future.
 */
public interface TransactionParser {

    /**
     * Attempt to extract a single transaction from raw text.
     *
     * @param rawText Raw SMS or email body (e.g. "Rs.500 debited from A/c XX1234 to AMAZON on 14-02-2026")
     * @return Parsed transaction if the text describes one, empty otherwise
     */
    Optional<TransactionParseResult> parse(String rawText);
}
