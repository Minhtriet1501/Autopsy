package com.minhtriet.autopsy.eval;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvalController {
    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping("/internal/eval/run")
    public EvalService.Report run() {
        return evalService.run();
    }
}
