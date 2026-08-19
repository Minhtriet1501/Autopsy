# Incident Investigation AI Agent

> An autonomous agent that investigates system incidents end-to-end: it ingests an alert,
> calls diagnostic tools, reasons over the evidence, and reports a root cause backed by a
> traceable evidence trail, with a self-authored agent loop (no LangChain).

**Eval result:** correctly identifies root cause in **3/3 injected-fault scenarios**,
averaging **~4 tool-calls** and **~$0.045 per investigation**.

---

## What it is

Two separate Spring Boot services:

- **Agent** (`autopsy`, port 8081), the investigator. A hand-written agent loop drives
  Claude (Anthropic Java SDK, tool-calling) to gather evidence and reach a conclusion.
- **Target** (`Job Tracker`, port 8082), the "patient." An existing app instrumented with
  observability + fault injection so the agent has real incidents to diagnose.

The agent talks to the target **only through read-only HTTP APIs**, no shared database.
Delete the target and the agent still runs, just point its tools at another system.

```
┌──────────────────────────┐      read-only HTTP APIs       ┌──────────────────────────┐
│  AGENT  (autopsy :8081)  │                                │  TARGET (Job Tracker     │
│                          │  -- query_logs ------------->  │          :8082)          │
│  • self-written loop     │     GET /internal/logs         │                          │
│  • tool registry         │  -- query_metrics ---------->  │  • JSON logging + MDC    │
│  • 3 cost guards         │     GET /actuator/metrics/*    │  • async batched log sink│
│  • evidence trail (JPA)  │                                │  • Micrometer / Actuator │
│  • Redis queue + worker  │                                │  • fault injection:      │
│  • token-bucket limiter  │                                │    slow-dependency, N+1  │
│  • eval harness          │                                │                          │
└───────────┬──────────────┘                                └──────────────────────────┘
            │ Redis (job queue + rate limit)
```

## How the agent works

```
investigate(alert):
  loop (bounded by MAX_STEPS and a token budget):
    ask Claude what to do next          # LLM decides, no hard-coded playbook
    if it concludes        -> save conclusion + evidence trail, return
    else run the tool it chose, feed the result back
    skip duplicate tool calls (loop guard)
  return INCONCLUSIVE                    # honest when the evidence runs out
```

The LLM drives: the prompt describes the tools and the goal, not "if timeout then
call query_metrics." Every conclusion is traceable to the tool outputs that produced it.

## Engineering highlights

- **Self-authored agent loop**: no framework, I own the `tool_use -> execute -> tool_result`
  cycle to control cost, loop detection, and the INCONCLUSIVE path.
- **Three independent cost guards**: step cap, token budget, and duplicate-call detection;
  each catches a different runaway (long loop / context explosion / literal repeat).
- **Evidence trail**: every investigation and each step (tool, args, result) is persisted;
  conclusions are auditable.
- **Eval harness**: runs scenarios with known injected faults and auto-scores accuracy,
  tool-calls, tokens, and cost.
- **Async processing**: Redis-backed job queue (LPUSH/BRPOP) + background worker: submit
  returns a job ID immediately (HTTP 202), clients poll for the result.
- **Token-bucket rate limiting**: atomic via a Redis Lua script.
- **Resilient LLM calls**: SDK retry with backoff + an explicit timeout (the single-threaded
  worker must not be blocked by a hung call).
- **Observability on the target**: structured JSON logging, MDC trace-ID correlation, an
  async batched log sink decoupled from the request path, and 60+ Micrometer metrics.

## Results

| Scenario | Injected fault | Correct? | Tool-calls | Cost |
|---|---|:---:|:---:|:---:|
| healthy | (none) | ✅ | 4 | $0.039 |
| slow_dependency | `SLOW_DEPENDENCY` | ✅ | 3 | $0.034 |
| n_plus_one | `N_PLUS_ONE` | ✅ | 5 | $0.063 |
| **Total** | | **3/3 (100%)** | **avg 4.0** | **avg $0.045** |

The `healthy` case matters: the agent reports *no incident* instead of inventing one — it
doesn't hallucinate a root cause when the system is fine.

## Tech stack

`Java 21` · `Spring Boot 3.4` · `Anthropic Java SDK (claude-sonnet-5, tool-calling)` ·
`Redis` · `Docker Compose` · `Spring Data JPA` · `Micrometer / Actuator` · `Maven`

## Running it

**Prerequisites:** Java 21, Docker, an `ANTHROPIC_API_KEY`, and the target service running on
port 8082.

```bash
# 1. start Redis
docker compose up -d

# 2. start the agent (reads ANTHROPIC_API_KEY from the environment)
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run                 # http://localhost:8081
```

Demo an investigation:

```bash
# inject a fault on the target and generate the symptom
curl -X POST localhost:8082/internal/faults/SLOW_DEPENDENCY/enable
curl localhost:8082/internal/faults/workload

# ask the agent to investigate (async -> returns a job id)
curl -X POST localhost:8081/internal/agent/investigate \
  -H "Content-Type: text/plain" -d "Job Tracker responding slowly"

# poll the job, then read the full evidence trail
curl localhost:8081/internal/agent/jobs/<jobId>
curl localhost:8081/internal/agent/investigations/<id>

# run the whole eval suite
curl -X POST localhost:8081/internal/eval/run
```

## Point it at your own system

This agent is meant to run **against your own system**, not as a public service: you run it
where you can reach your app, and it holds *your* Anthropic key. `Job Tracker` above is just a
demo target. To investigate your own app, point `TARGET_BASE_URL` at it and have it expose what
the two tools call:

**1. Log query** (`GET {TARGET_BASE_URL}/internal/logs`) with optional query params
`traceId`, `level`, `contains`, `limit`, returning a JSON array of entries:

```json
[{ "eventTime": "...", "level": "WARN", "logger": "...", "message": "...", "traceId": "..." }]
```

**2. Metrics** in Spring Boot Actuator format: `GET {TARGET_BASE_URL}/actuator/metrics` (the
list) and `/actuator/metrics/{name}` (one metric).

**Different API shape?** The tools are pluggable: implement `InvestigationTool` (or adapt
`TargetSystemClient`) to call your own logging / metrics / tracing backend. The agent loop
doesn't care where the evidence comes from.

Point the agent at your app:

```bash
TARGET_BASE_URL=http://your-app:port ./mvnw spring-boot:run
```

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/agent/investigate` | Submit an alert → returns `{jobId}` (202) |
| `GET`  | `/internal/agent/jobs/{id}` | Job status (QUEUED / RUNNING / DONE / FAILED) |
| `GET`  | `/internal/agent/investigations/{id}` | Full evidence trail |
| `POST` | `/internal/eval/run` | Run the eval suite → accuracy / steps / cost |


## License

MIT License. See [`LICENSE`](LICENSE).
