package com.minhtriet.autopsy.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.time.Instant;

@Entity
@Table(name = "evidence_steps")
@Setter
@Getter
public class EvidenceStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id")
    @JsonIgnore //avoid loop
    private Investigation investigation;

    private int stepNo;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String toolArgs;

    @Column(columnDefinition = "TEXT")
    private String toolResult;

    private Instant createdAt;
}
