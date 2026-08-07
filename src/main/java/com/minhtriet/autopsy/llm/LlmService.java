package com.minhtriet.autopsy.llm;


import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.stereotype.Service;


@Service
public class LlmService {

    private final AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    public String ask(String systemPrompt, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-sonnet-5")
                .maxTokens(1024)
                .system(systemPrompt)
                .addUserMessage(userMessage).build();

        Message response = client.messages().create(params);

        StringBuilder sb = new StringBuilder();
        response.content().stream()
                .flatMap(block -> block.text().stream()) //.text return Optional<block>,if Optional is null stream is empty -> ignore null Optional
                .forEach(textBlock -> sb.append(textBlock.text()));

        return sb.toString();
    }
}
