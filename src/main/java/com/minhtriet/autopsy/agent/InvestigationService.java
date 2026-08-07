package com.minhtriet.autopsy.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.minhtriet.autopsy.tool.InvestigationTool;
import com.minhtriet.autopsy.tool.TargetSystemClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvestigationService {

    private final int MAX_STEPS = 8; //cost cap no infinity loop

    private final AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    private final List<InvestigationTool> tools;
    private final Map<String, InvestigationTool> registry;

    public InvestigationService(List<InvestigationTool> tools) {
        this.tools = tools;
        this.registry = tools.stream().collect(Collectors.toMap(InvestigationTool::name, t -> t));
    }



    public String investigate(String alert) {
        String system = """
                You are a system incident investigation engineer. You have a query_logs tool to read the Job Tracker's logs.
                Your task: starting from an alert, use query_logs to gather evidence, reason about the root cause, then give a SHORT conclusion.
                Every conclusion must be grounded in specific evidence from the logs. If there isn't enough evidence, say plainly "not enough evidence to conclude".
                """;
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("ALERTS: " + alert)
                .build());


        for(int step = 1; step <= MAX_STEPS; step++) {
            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model("claude-sonnet-5")
                    .maxTokens(2048L)
                    .system(system)
                    .messages(messages);
            for(InvestigationTool tool : tools) {
                builder.addTool(tool.definition());
            }

            Message response = client.messages().create(builder.build());

            messages.add(response.toParam()); //record claude's turn in the history

            boolean wantsTool = response.stopReason()
                    .map(sr -> sr.equals(StopReason.TOOL_USE))
                    .orElse(false);

            if(!wantsTool) { //Claude has concluded (no more tool)
                return extractText(response);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for(ContentBlock block : response.content()) {
                if(block.isToolUse()) {
                    ToolUseBlock tu = block.asToolUse();
                    System.out.println("[agent] step: " + step + ": Claude called " + tu.name() + " " + tu._input());
                    toolResults.add(ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                    .toolUseId(tu.id())
                                    .content(executeTool(tu))
                                    .build()
                    ));
                }
            }

            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        return "Inconclusive: reached the " + MAX_STEPS + "-step limit without a conclusion.";
    }


    private JsonValue strProp(String description) {
        return JsonValue.from(Map.of("type", "string", "description", description));
    }

    @SuppressWarnings("unchecked")
    private String executeTool(ToolUseBlock tu) {
        InvestigationTool tool = registry.get(tu.name());
        if(tool == null) {
            return "Error: no tool named " + tu.name();
        }

        Map<String, Object> args = tu._input().convert(Map.class);
        try {
            return tool.execute(args);
        } catch (Exception e) {
            return"Error calling "+ tu.name() + e.getMessage(); //tool failed -> report back to claude
        }
    }

    private String extractText(Message response) {
        StringBuilder sb = new StringBuilder();
        response.content().forEach(b -> b.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString();
    }



}
