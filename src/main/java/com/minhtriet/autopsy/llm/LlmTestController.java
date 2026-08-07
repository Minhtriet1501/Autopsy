package com.minhtriet.autopsy.llm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmTestController {

    private final LlmService llmService;

    public LlmTestController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping("/internal/llm/ping")
    public String ping(@RequestParam(defaultValue = "Chào một câu ngắn bằng tiếng Việt.") String q) {
        return llmService.ask("Bạn là trợ lý trả lời ngắn gọn.", q);
    }

}