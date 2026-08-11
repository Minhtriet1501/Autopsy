package com.minhtriet.autopsy.eval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class FaultControlClient {
    private final RestClient restClient;

    public FaultControlClient(@Value("${target.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public void enable (String faultType) {
        restClient.post().uri("/internal/faults/{t}/enable", faultType).retrieve().toBodilessEntity();
    }

    public void disable (String faultType) {
        restClient.post().uri("/internal/faults/{t}/disable", faultType).retrieve().toBodilessEntity();
    }

    public void disableAll () {
        for (String t : List.of("SLOW_DEPENDENCY", "N_PLUS_ONE", "POOL_EXHAUSTION")) {
            disable(t);
        }
    }

    public void triggerWorkload() {
        restClient.get().uri("/internal/faults/workload").retrieve().toBodilessEntity();
    }
}
