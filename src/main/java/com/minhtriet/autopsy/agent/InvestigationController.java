package com.minhtriet.autopsy.agent;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/agent")
public class InvestigationController {
    private final InvestigationService investigationService;


    @PostMapping("/investigate")
    public String investigation(@RequestBody String alert) {
        return investigationService.investigate(alert);
    }
}
