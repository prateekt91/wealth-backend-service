package com.wealthmanager.backend.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Collections;

/**
 * Manual Ollama chat model configuration when not using spring-ai-ollama-spring-boot-starter.
 * Registers a ChatModel bean for use by LlmTransactionParser.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class OllamaConfig {

    @Value("${app.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${app.ai.ollama.chat-model:llama3.2}")
    private String chatModel;

    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager(
            @Autowired(required = false) ObservationRegistry observationRegistry) {
        return DefaultToolCallingManager.builder()
                .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
                .toolCallbackResolver(new StaticToolCallbackResolver(Collections.emptyList()))
                .toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder().build())
                .build();
    }

    @Bean("ollamaChatModel")
    public ChatModel ollamaChatModel(OllamaApi ollamaApi, ToolCallingManager toolCallingManager) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(chatModel)
                .build();
        return new OllamaChatModel(ollamaApi, options, toolCallingManager, ObservationRegistry.NOOP, ModelManagementOptions.defaults());
    }
}
