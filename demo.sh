#!/usr/bin/env bash
BASE=localhost:8081/internal; A=$BASE/agent; T=localhost:8082/internal/faults

echo "▶ 1. Inject a fault into the target"
curl -s -X POST $T/SLOW_DEPENDENCY/enable >/dev/null; curl -s $T/workload >/dev/null
echo "   slow-dependency enabled + symptom generated"

echo; echo "▶ 2. Ask the agent"
JOB=$(curl -s -X POST $A/investigate -H "Content-Type: text/plain" -d "Job Tracker slow" | jq -r .jobId)
echo "   submitted → job $JOB"

echo; printf "▶ 3. Investigating"
until [ "$(curl -s $A/jobs/$JOB | jq -r .status)" = "DONE" ]; do printf '.'; sleep 2; done
echo " done"
ID=$(curl -s $A/jobs/$JOB | jq -r .investigationId)

echo; echo "▶ 4. Summary"
curl -s $A/investigations/$ID | jq '{status, steps, estCostUsd}'

echo; echo "▶ 5. Root cause (agent's own words)"
curl -s $A/investigations/$ID | jq -r '.conclusion'

echo; echo "▶ 6. Eval suite"
curl -s -X POST $BASE/eval/run | jq '{accuracy, avgSteps, avgCostUsd}'
