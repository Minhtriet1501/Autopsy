package com.minhtriet.autopsy.eval;


import com.minhtriet.autopsy.agent.Investigation;
import com.minhtriet.autopsy.agent.InvestigationService;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvalService {

    public record Scenario(String name, String faultType, String alert, List<String> expected) {};

    public record Result(String scenario, boolean correct, int steps,long token, double costUsd, String conclusion) {};

    public record Report(long passed, int total, double accuracy, double avgSteps,double avgTokens, double avgCostUsd, List<Result> results) {};

    private final FaultControlClient faultControl;
    private final InvestigationService investigationService;

    public EvalService(FaultControlClient faultControl,  InvestigationService investigationService) {
        this.faultControl = faultControl;
        this.investigationService = investigationService;
    }

    public Report run() {
        List<Result> results = new ArrayList<>();
        for(Scenario s : scenarios()) {
            faultControl.disableAll();
            if(s.faultType() != null) {
                faultControl.enable(s.faultType());
            }
            faultControl.triggerWorkload(); //create symptom

            Investigation inv = investigationService.investigate(s.alert());
            String conclusion = inv.getConclusion() == null ? "" : inv.getConclusion();

            boolean correct = s.expected().stream().anyMatch(y -> conclusion.toLowerCase().contains(y.toLowerCase()));
            long tokens = inv.getInputTokens() + inv.getOutputTokens();

            results.add(new Result(s.name(), correct, inv.getSteps(), tokens, inv.getEstCostUsd(), conclusion.length() > 200 ? conclusion.substring(0, 200) + "..." : conclusion));
        }
        faultControl.disableAll();

        long passed = results.stream().filter(Result::correct).count();
        double avgSteps = results.stream().mapToInt(Result::steps).average().orElse(0);
        double avgTokens = results.stream().mapToLong(Result::token).average().orElse(0);
        double avgCost = results.stream().mapToDouble(Result::costUsd).average().orElse(0);
        return new Report(passed, results.size(), (double) passed / results.size(), avgSteps, avgTokens, avgCost, results);
    }

    private List<Scenario> scenarios() {
        return List.of(
                new Scenario("healthy", null,
                        "Routine health check on Job Tracker.",
                        List.of("no evidence", "healthy", "cannot conclude", "insufficient", "no issue", "inconclusive")),
                new Scenario("slow_dependency", "SLOW_DEPENDENCY",
                        "Users report Job Tracker responding slowly.",
                        List.of("dependency", "slow", "slow_dependency")),
                new Scenario("n_plus_one", "N_PLUS_ONE",
                        "Job Tracker DB access is inefficient under load.",
                        List.of("n+1", "n_plus_one", "query")));
    }


}
