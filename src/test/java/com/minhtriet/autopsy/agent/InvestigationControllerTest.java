package com.minhtriet.autopsy.agent;


import com.minhtriet.autopsy.job.InvestigationJobService;
import com.minhtriet.autopsy.ratelimit.TokenBucketRateLimit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestigationController.class)
public class InvestigationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TokenBucketRateLimit rateLimiter;

    @MockitoBean
    InvestigationJobService jobService;

    @MockitoBean
    InvestigationRepository investigationRepository;


    @Test
    void returns202AndJobId_whenAllowed() throws Exception {
        when(rateLimiter.allow("investigate")).thenReturn(true);
        when(jobService.submit("Job Tracker slow")).thenReturn("job-123");

        mvc.perform(post("/internal/agent/investigate")
                .contentType("text/plain").content("Job Tracker slow"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void returns429_whenRateLimited() throws Exception {
        when(rateLimiter.allow("investigate")).thenReturn(false);

        mvc.perform(post("/internal/agent/investigate")
                        .contentType("text/plain").content("whatever"))
                .andExpect(status().isTooManyRequests());            // 429
    }
}
