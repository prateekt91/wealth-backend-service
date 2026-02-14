package com.wealthmanager.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String BRIDGE_PATH_PREFIX = "/api/v1/bridge";
    private static final String HEALTH_PATH = "/api/v1/bridge/health";

    private final String apiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(@Value("${app.security.api-key}") String apiKey,
                            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip API key check for non-bridge endpoints and the health endpoint
        if (!requestPath.startsWith(BRIDGE_PATH_PREFIX) || requestPath.equals(BRIDGE_PATH_PREFIX + "/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank()) {
            log.warn("Missing API key for bridge request from {}", request.getRemoteAddr());
            sendUnauthorizedResponse(response, "Missing API key. Provide X-API-KEY header.");
            return;
        }

        if (!apiKey.equals(providedKey)) {
            log.warn("Invalid API key for bridge request from {}", request.getRemoteAddr());
            sendUnauthorizedResponse(response, "Invalid API key.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "Unauthorized",
                "message", message
        );

        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
