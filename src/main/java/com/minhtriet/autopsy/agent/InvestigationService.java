package com.minhtriet.autopsy.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.minhtriet.autopsy.tool.InvestigationTool;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InvestigationService {

    //claude-sonnet-5 intro pricing đến 31/08/2026 ($3/$15 sau đó)
    private final double INPUT_PRICE_PER_1M = 2.00;
    private final double OUTPUT_PRICE_PER_1M = 10.00;

    private final int MAX_STEPS = 8; //cost cap no infinity loop
    private final long MAX_TOKENS = 50_000;

    private final InvestigationRepository investigationRepository;

    private final AnthropicClient client;

    private final List<InvestigationTool> tools;

    private final Map<String, InvestigationTool> registry;

    public InvestigationService(List<InvestigationTool> tools, AnthropicClient client, InvestigationRepository investigationRepository) {
        this.investigationRepository = investigationRepository;
        this.client = client;
        this.tools = tools;
        this.registry = tools.stream().collect(Collectors.toMap(InvestigationTool::name, t -> t));
    }



    public Investigation investigate(String alert) {
        long totalInput = 0, totalOutput = 0;
        Set<String> seenCalls = new HashSet<>(); //store what tools were used

        Investigation inv = new Investigation();
        inv.setAlert(alert);
        inv.setCreatedAt(java.time.Instant.now());

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

            if(totalInput + totalOutput >= MAX_TOKENS) {
                return finish(inv, "INCONCLUSIVE", "INCONCLUSIVE: token budget (" + MAX_TOKENS + ") exhausted after " + (step - 1) + " steps.", step - 1, totalInput, totalOutput);
            }

            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model("claude-sonnet-5")
                    .maxTokens(2048L)
                    .system(system)
                    .messages(messages);
            for(InvestigationTool tool : tools) {
                builder.addTool(tool.definition());
            }

            Message response = client.messages().create(builder.build());

            //get token that were used
            totalInput += response.usage().inputTokens();
            totalOutput += response.usage().outputTokens();


            messages.add(response.toParam()); //record claude's turn in the history

            String reasoning = extractText(response);

            boolean wantsTool = response.stopReason()
                    .map(sr -> sr.equals(StopReason.TOOL_USE))
                    .orElse(false);

            if(!wantsTool) { //Claude has concluded (no more tool)
                return finish(inv, "CONCLUDED", reasoning, step, totalInput, totalOutput);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for(ContentBlock block : response.content()) {
                if(block.isToolUse()) {
                    ToolUseBlock tu = block.asToolUse();
                    System.out.println("[agent] step: " + step + ": Claude called " + tu.name() + " " + tu._input());

                    String signature = tu.name() + "|"  + tu._input();

                    String result;

                    if(!seenCalls.add(signature)) {
                        result = "Duplicate call: you already ran" + tu.name()
                                + " with these exact arguments; the result is unchanged. "
                                + "Try a different tool or arguments, or conclude with the evidence you already have.";
                    }
                    else {
                        result = executeTool(tu);
                    }



                    EvidenceStep es = new EvidenceStep();
                    es.setStepNo(step);
                    es.setReasoning(reasoning);
                    es.setToolName(tu.name());
                    es.setToolArgs(tu._input().toString());
                    es.setToolResult(truncate(result, 2000));
                    es.setCreatedAt(java.time.Instant.now());
                    inv.addStep(es);

                    toolResults.add(ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                    .toolUseId(tu.id())
                                    .content(result)
                                    .build()
                    ));
                }
            }

            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        inv.setConclusion("INCONCLUSIVE: reach the " + MAX_STEPS + "-step limit without a conclusion.");
        inv.setStatus("INCONCLUSIVE");
        inv.setSteps(MAX_STEPS);
        return finish(inv, "INCONCLUSIVE",
                "INCONCLUSIVE: reach the " + MAX_STEPS + "-step limit without a conclusion.",
                MAX_STEPS, totalInput, totalOutput);
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

    private String truncate(String result, int max) {
        if (result == null) return null;

        return result.length() <= max ? result : result.substring(0, max) + "[truncated]";

    }

    private Investigation finish(Investigation inv, String status, String conclusion,
                                 int steps, long in, long out) {
        inv.setConclusion(conclusion);
        inv.setStatus(status);
        inv.setSteps(steps);
        inv.setInputTokens(in);
        inv.setOutputTokens(out);
        inv.setEstCostUsd(in / 1_000_000.0 * INPUT_PRICE_PER_1M + out / 1_000_000.0 * OUTPUT_PRICE_PER_1M);

        return investigationRepository.save(inv);
    }

}
