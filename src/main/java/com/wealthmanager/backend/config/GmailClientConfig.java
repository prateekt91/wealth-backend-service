package com.wealthmanager.backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
@Slf4j
public class GmailClientConfig {

    private static final String APPLICATION_NAME = "wealth-manager";
    public static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    @Getter
    @Value("${app.gmail.client-id:}")
    private String clientId;

    @Getter
    @Value("${app.gmail.client-secret:}")
    private String clientSecret;

    @Value("${app.gmail.refresh-token:}")
    private String refreshToken;

    /** Whether a refresh token is configured (without exposing the token). */
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    @Getter
    @Value("${app.gmail.redirect-uri:http://localhost:8080/api/v1/bridge/gmail/callback}")
    private String redirectUri;

    @Value("${app.gmail.poll-interval-ms:60000}")
    @Getter
    private long pollIntervalMs;

    @Value("${app.gmail.initial-lookback-minutes:1440}")
    @Getter
    private int initialLookbackMinutes;

    @Value("${app.gmail.search-keywords:debit,credit,transaction,payment}")
    @Getter
    private String searchKeywords;

    @Bean
    public NetHttpTransport netHttpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public GsonFactory gsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.gmail", name = "enabled", havingValue = "true")
    public Gmail gmailService(NetHttpTransport httpTransport, GsonFactory jsonFactory)
            throws IOException {

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Gmail refresh token not configured. Use /api/v1/bridge/gmail/auth-url to authorize.");
            return null;
        }

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        Gmail service = new Gmail.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        log.info("Gmail API client initialized successfully");
        return service;
    }
}
