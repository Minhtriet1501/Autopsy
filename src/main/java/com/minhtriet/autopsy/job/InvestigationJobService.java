package com.minhtriet.autopsy.job;


import com.minhtriet.autopsy.agent.Investigation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestigationJobService {

    static final String QUEUE_KEY = "agent:queue";
    static final String JOB_KEY_PREFIX = "agent:job:";

    private final StringRedisTemplate redis;

    public InvestigationJobService(final StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String submit(String alert) {
        String jobId =  UUID.randomUUID().toString();
        String key = JOB_KEY_PREFIX + jobId;

        Map<String, String> job = new HashMap<>();
        job.put("status", "QUEUED");
        job.put("alert", alert);
        redis.opsForHash().putAll(key, job);
        redis.expire(key, Duration.ofHours(1));

        redis.opsForList().leftPush(QUEUE_KEY, jobId);

        return jobId;
    }

    public Map<Object, Object> status(String jobId) {
        return redis.opsForHash().entries(JOB_KEY_PREFIX + jobId);
    }
}
