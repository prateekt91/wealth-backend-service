package com.wealthmanager.backend.controller;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.GmailScopes;
import com.wealthmanager.backend.config.GmailClientConfig;
import com.wealthmanager.backend.service.GmailPollerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-time OAuth2 setup endpoints for Gmail integration.
 * <p>
 * Flow:
 * 1. GET /api/v1/bridge/gmail/auth-url → Returns the Google consent URL
 * 2. User visits that URL, grants consent, gets redirected back
 * 3. GET /api/v1/bridge/gmail/callback?code=xxx → Exchanges code for refresh token
 * 4. User stores the refresh token as GMAIL_REFRESH_TOKEN env variable
 * 5. Restart the app with GMAIL_ENABLED=true
 */
@RestController
@RequestMapping("/api/v1/bridge/gmail")
@Slf4j
public class GmailOAuthController {

    private final GmailClientConfig gmailConfig;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;
    private final Optional<GmailPollerService> gmailPollerService;

    public GmailOAuthController(GmailClientConfig gmailConfig,
                                NetHttpTransport httpTransport,
                                GsonFactory jsonFactory,
                                Optional<GmailPollerService> gmailPollerService) {
        this.gmailConfig = gmailConfig;
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.gmailPollerService = gmailPollerService;
    }

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getAuthorizationUrl() {
        String clientId = gmailConfig.getClientId();

        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Gmail client_id not configured",
                    "message", "Set GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET environment variables first."
            ));
        }

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(gmailConfig.getRedirectUri(), StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(GmailScopes.GMAIL_READONLY, StandardCharsets.UTF_8) +
                "&access_type=offline" +
                "&prompt=consent";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authUrl", authUrl);
        response.put("instructions", "Visit the authUrl in your browser, grant access, " +
                "and you will be redirected back with a refresh token.");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleOAuthCallback(@RequestParam String code) {
        String clientId = gmailConfig.getClientId();
        String clientSecret = gmailConfig.getClientSecret();

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Gmail OAuth credentials not configured",
                    "message", "Set GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET environment variables."
            ));
        }

        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    httpTransport,
                    jsonFactory,
                    "https://oauth2.googleapis.com/token",
                    clientId,
                    clientSecret,
                    code,
                    gmailConfig.getRedirectUri()
            ).execute();

            String refreshToken = tokenResponse.getRefreshToken();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("refreshToken", refreshToken);
            response.put("accessToken", tokenResponse.getAccessToken());
            response.put("expiresInSeconds", tokenResponse.getExpiresInSeconds());
            response.put("instructions",
                    "Store the refreshToken as the GMAIL_REFRESH_TOKEN environment variable, " +
                    "then set GMAIL_ENABLED=true and restart the application.");

            log.info("Gmail OAuth2 authorization successful. Refresh token obtained.");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Failed to exchange OAuth code for tokens: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Token exchange failed",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGmailStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean credentialsSet = gmailConfig.getClientId() != null && !gmailConfig.getClientId().isBlank()
                && gmailConfig.getClientSecret() != null && !gmailConfig.getClientSecret().isBlank();
        status.put("enabled", credentialsSet);
        status.put("hasRefreshToken", gmailConfig.hasRefreshToken());
        status.put("schedulerActive", gmailPollerService.isPresent());
        status.put("pollIntervalMs", gmailConfig.getPollIntervalMs());
        status.put("searchKeywords", gmailConfig.getSearchKeywords());
        gmailPollerService
                .map(GmailPollerService::getLastPollTimeMs)
                .filter(t -> t != null)
                .ifPresent(ms -> status.put("lastPollAt", Instant.ofEpochMilli(ms).toString()));
        return ResponseEntity.ok(status);
    }
}
