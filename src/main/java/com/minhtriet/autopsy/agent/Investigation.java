package com.minhtriet.autopsy.agent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investigations")
@Getter
@Setter
public class Investigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(columnDefinition = "TEXT")
    private String alert;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    private String status; //inconclusive or concluded

    private int steps;

    private long inputTokens;

    private long outputTokens;

    private double estCostUsd;

    private Instant createdAt;


    @OneToMany(mappedBy = "investigation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    private List<EvidenceStep> evidenceSteps = new ArrayList<>();

    public void addStep(EvidenceStep step) {
        step.setInvestigation(this);
        evidenceSteps.add(step);
    }





}
