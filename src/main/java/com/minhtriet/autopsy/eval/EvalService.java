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

    public record Result(String scenario, boolean correct, int steps, String conclusion) {};

    public record Report(long passed, int total, double accuracy, double avgSteps, List<Result> results) {};

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

            results.add(new Result(s.name(), correct, inv.getSteps(), conclusion.length() > 200 ? conclusion.substring(0, 200) + "..." : conclusion));
        }
        faultControl.disableAll();

        long passed = results.stream().filter(Result::correct).count();
        double avgSteps = results.stream().mapToInt(Result::steps).average().orElse(0);
        return new Report(passed, results.size(), (double) passed / results.size(), avgSteps, results);
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
