package org.emtech.Tools;  // or org.emtech.Config — your choice

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfig {   // better name than Configurations or WebClientConfig

    // ────────────────────────────────────────────────
    // 1. Scheduler configuration (fixes your parallel execution problem)
    // ────────────────────────────────────────────────
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("ForexTask-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        // Optional: log uncaught exceptions from scheduled tasks
        // scheduler.setErrorHandler(t -> log.error("Scheduled task failed", t));
        return scheduler;
    }

    // ────────────────────────────────────────────────
    // 2. Custom WebClient with insecure SSL (for self-signed/test certs)
    // ────────────────────────────────────────────────
    @Bean
    public WebClient webClient() throws Exception {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // You can also move your property loading here if you want
    // (but it's fine to keep it separate in Configurations)
}
