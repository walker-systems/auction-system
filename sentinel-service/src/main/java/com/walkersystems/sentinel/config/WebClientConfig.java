package com.walkersystems.sentinel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient fastApiWebClient() {
        return WebClient.builder()
                // TODO: Replace URL with injection from application.yml for real internal DNS name
                .baseUrl("http://localhost:8000")
                .build();
    }
}
