package com.minhtriet.autopsy.agent;


import com.minhtriet.autopsy.job.InvestigationJobService;
import com.minhtriet.autopsy.ratelimit.TokenBucketRateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/agent")
public class InvestigationController {
    private final InvestigationRepository investigationRepository;
    private final InvestigationJobService jobService;
    private final TokenBucketRateLimit rateLimiter;

    public InvestigationController(InvestigationRepository investigationRepository,InvestigationJobService jobService, TokenBucketRateLimit rateLimiter) {
        this.investigationRepository = investigationRepository;
        this.jobService = jobService;
        this.rateLimiter = rateLimiter;
    }


    @PostMapping("/investigate")
    public ResponseEntity<Map<String, String>> investigation(@RequestBody String alert) {
        if(!rateLimiter.allow("investigate")) {
            return ResponseEntity.status(429).body(Map.of("error", "rate limit exceeded, slow down"));
        }
        String jobId = jobService.submit(alert);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "QUEUED")); //202

    }
    @GetMapping("/jobs/{jobId}")
    public Map<Object, Object> job(@PathVariable String jobId) {
        return jobService.status(jobId);
    }

    @GetMapping("/investigations")
    public List<Investigation> list() {
        return investigationRepository.findAll();
    }

    @GetMapping("/investigations/{id}")
    public Investigation get(@PathVariable Long id) {
        return investigationRepository.findById(id).orElseThrow();
    }

}
