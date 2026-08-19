package com.minhtriet.autopsy.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.timeout-seconds}") long timeoutSeconds,
                                           @Value("${anthropic.max-retries}")  int maxRetries) {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
    }
}
