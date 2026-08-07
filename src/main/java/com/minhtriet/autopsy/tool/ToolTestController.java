package com.minhtriet.autopsy.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ToolTestController {
    private final TargetSystemClient targetSystemClient;


    @GetMapping("/internal/agent/test-logs")
    public String testLogs(@RequestParam(required = false) String traceId) {
        return targetSystemClient.queryLogs(traceId, null, null, 20);
    }
}
