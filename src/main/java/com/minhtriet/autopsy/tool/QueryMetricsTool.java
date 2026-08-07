package com.minhtriet.autopsy.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryMetricsTool implements InvestigationTool {
    private final TargetSystemClient  targetSystemClient;

    @Override
    public String name() {
        return "query_metrics";
    }

    @Override
    public Tool definition() {
        return Tool.builder()
                .name("query_metrics")
                .description("Read runtime metrics from the target system (Micrometer/Actuator). "
                        + "Call with NO 'metric' to list all available metric names. "
                        + "Call with a 'metric' name (e.g. http.server.requests, hikaricp.connections.pending, "
                        + "jvm.memory.used) to get its current value.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("metric", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "metric name to fetch; omit to list all metric names"
                                )))
                                .build())
                        .build())
                .build();
    }
    @Override
    public String execute(Map<String, Object> args) {
        String metric =  (String) args.get("metric");
        if(metric == null || metric.isBlank()) {
            return targetSystemClient.listMetrics();
        }
        return targetSystemClient.getMetric(metric);
    }
}
