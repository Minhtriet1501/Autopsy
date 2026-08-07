package com.minhtriet.autopsy.tool;


import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.minhtriet.autopsy.agent.InvestigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryLogsTool implements InvestigationTool {
    private final TargetSystemClient targetSystemClient;


    @Override
    public String name() {
        return "query_logs";
    }

    @Override
    public Tool definition() {
        return Tool.builder()
                .name("query_logs")
                .description("Read logs from the Job Tracker system. Filter by traceId, level "
                        + "(INFO/WARN/ERROR/DEBUG), contains (a substring in the message), and limit "
                        + "(max number of rows). Any parameter left empty is not applied as a filter.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("traceId", strProp("traceId of a single request"))
                                .putAdditionalProperty("level", strProp("log level: INFO, WARN, ERROR, DEBUG"))
                                .putAdditionalProperty("contains", strProp("only logs whose message contains this substring"))
                                .putAdditionalProperty("limit", JsonValue.from(Map.of("type", "integer", "description", "max rows, default 100")))
                                .build())
                        .build())
                .build();
    }

    @Override
    public String execute(Map<String,Object> args) {
        String traceId = (String) args.get("traceId");
        String level = (String) args.get("level");
        String contains = (String) args.get("contains");
        Integer limit = (Integer) args.get("limit") == null ? null : ((Number) args.get("limit")).intValue();

        return targetSystemClient.queryLogs(traceId, level, contains, limit);
    }


    private JsonValue strProp(String description) {
        return JsonValue.from(Map.of("type", "string", "description", description));
    }
}
