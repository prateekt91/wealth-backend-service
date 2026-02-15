package com.wealthmanager.backend.ai.impl;

import com.wealthmanager.backend.ai.TransactionParser;
import com.wealthmanager.backend.model.dto.TransactionParseResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * No-op parser when app.ai.enabled=false. Returns empty for every parse.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false")
public class NoOpTransactionParser implements TransactionParser {

    @Override
    public Optional<TransactionParseResult> parse(String rawText) {
        return Optional.empty();
    }
}
