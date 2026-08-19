package com.minhtriet.autopsy.job;

import com.minhtriet.autopsy.agent.Investigation;
import com.minhtriet.autopsy.agent.InvestigationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class InvestigationJobWorker {

    private final StringRedisTemplate redis;
    private final InvestigationService investigationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public InvestigationJobWorker(StringRedisTemplate redis, InvestigationService investigationService) {
        this.redis = redis;
        this.investigationService = investigationService;
    }

    @PostConstruct
    public void start() {
        executor.submit(this::loop);
    }

    private void loop() {
        while (running) {
            try {
                String jobId = redis.opsForList().rightPop(InvestigationJobService.QUEUE_KEY, Duration.ofSeconds(2));
                if(jobId == null) {
                    continue;
                }
                process(jobId);
            } catch (Exception e) {
                System.out.println("[worker] error: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private void process(String jobId) {
        String key = InvestigationJobService.JOB_KEY_PREFIX + jobId;
        try {
            redis.opsForHash().put(key, "status", "RUNNING");
            String alert= (String) redis.opsForHash().get(key, "alert");

            Investigation inv = investigationService.investigate(alert);

            redis.opsForHash().put(key, "status", "DONE");
            redis.opsForHash().put(key, "investigationId", String.valueOf(inv.getId()));
            System.out.println("[worker] job " + jobId + " DONE -> investigation " + inv.getId());
        } catch (Exception e) {
            redis.opsForHash().put(key, "status", "FAILED");
            redis.opsForHash().put(key, "error", String.valueOf(e.getMessage()));
            System.out.println("[worker] job " + jobId + " FAILED -> " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdown();
    }


}
