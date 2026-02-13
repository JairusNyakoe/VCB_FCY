package org.emtech.Tools;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

@Component
public class LogIn {

    private final WebClient webClient;
    private final Configurations configurations;

    private volatile String accessToken;
    private volatile LocalDateTime tokenExpiration;

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public LogIn(WebClient webClient, Configurations configurations) {
        this.webClient = webClient;
        this.configurations = configurations;
    }

    // ✅ Generate token when application starts
    @PostConstruct
    public void init() {
        authenticate();
    }

    // ✅ Refresh token every 2 hours
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void refreshTokenAutomatically() {
        System.out.println("Refreshing token automatically...");
        authenticate();
    }

    public synchronized void authenticate() {

        Properties prop = configurations.getProperties();
        String loginUrl = prop.getProperty("logInUrl");

        Map<String, String> requestBody = Map.of(
                "userUser", prop.getProperty("user"),
                "userPass", prop.getProperty("password")
        );

        LoginResponse response = webClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(LoginResponse.class)
                .block();

        if (response != null && response.isAuthenticated()) {
            this.accessToken = response.getAccessToken();
            this.tokenExpiration =
                    LocalDateTime.parse(response.getExpiration(), formatter);

            System.out.println("Token generated successfully. Expires at: "
                    + tokenExpiration);
        } else {
            throw new RuntimeException("Authentication failed");
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    @Data
    @NoArgsConstructor
    private static class LoginResponse {
        private boolean authenticated;
        private String created;
        private String expiration;
        private String accessToken;
        private String message;
    }
}
