package com.minhtriet.autopsy.agent;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/agent")
public class InvestigationController {
    private final InvestigationRepository investigationRepository;
    private final InvestigationService investigationService;


    @PostMapping("/investigate")
    public Investigation investigation(@RequestBody String alert) {
        return investigationService.investigate(alert);
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
