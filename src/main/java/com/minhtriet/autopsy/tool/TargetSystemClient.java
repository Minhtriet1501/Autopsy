package com.minhtriet.autopsy.tool;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class TargetSystemClient {
    private final RestClient restClient;

    public TargetSystemClient(@Value("${target.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public String queryLogs(String traceId, String level, String contains, Integer limit) {
        return restClient.get()
                .uri(b -> b.path("/internal/logs")
                        .queryParamIfPresent("traceId", Optional.ofNullable(traceId))
                        .queryParamIfPresent("level", Optional.ofNullable(level))
                        .queryParamIfPresent("contains", Optional.ofNullable(contains))
                        .queryParam("limit", limit == null ? 100 : limit)
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String listMetrics() {
        return restClient.get()
                .uri("/actuator/metrics")
                .retrieve()
                .body(String.class);
    }

    public String getMetric(String metric) {
        return restClient.get()
                .uri(b -> b.path("actuator/metrics/{name}").build(metric))
                .retrieve()
                .body(String.class);
    }
}
