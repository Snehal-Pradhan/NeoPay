# DevOps Checklist

Operating backlog for the NeoPay microservices project.

**Premise:** the JMeter run is assumed complete — results are treated as available and are not tracked here.
**Rule:** every checked box must leave an artifact in the repo or a screenshot in `docs/evidence/`. No artifact, no credit.
**Target:** move the project from "tutorial microservice clone" to "built, shipped, observed, and operated."

Legend: `[ ]` todo · `[x]` done · `[~]` optional / cut candidate

---

## Score model (why each phase exists)

| Phase | Points | What it proves |
|---|---:|---|
| 0. Credibility | +1.0 | You are not a security liability |
| 1. CI + images | +1.5 | The build is real and repeatable |
| 2. Infra as code | +1.0 | You can create and destroy the platform |
| 3. GitOps | +0.75 | You can deploy safely and roll back |
| 4. Observability | +1.25 | You can see inside a running system |
| 5. Supply chain | +0.5 | You understand attack surface |
| 6. SRE practice | +0.75 | You can run it, not just deploy it |
| 7. Evidence + writing | +1.0 | You can explain and defend it |

Total if fully applied: **+7.75**, which is the difference between a 5 and an 8.5.

---

## Phase 0 — Credibility (do this before anything else)

- [x] Rotate the 50 credentials in `k8s/api_keys.csv` — assume all are compromised
- [x] Rotate the `np_live_...` key in `k8s/NeoPay Load Testing.jmx`
- [x] Delete `k8s/api_keys.csv` and strip the Authorization Manager from the JMX
- [x] Replace `k8s/k8s-secrets.env` with `k8s-secrets.env.example` (placeholders only)
- [x] Purge both from git history — the repository uses a fresh history without the old blobs
- [x] Add `gitleaks` CI scan so it cannot recur
- [x] Rename to the `com.snehal.neopay` namespace
- [x] Add a README line owning the lineage

**Done when:** a fresh clone contains zero credentials, and `git log --all -p | grep -i 'np_'` returns nothing.

**Why:** a single committed key ends the review regardless of how good the architecture is.

---

## Phase 1 — CI and images

- [ ] One pipeline, GitHub Actions only. Do not also ship Jenkins and GitLab CI in the repo — mention them in interviews instead
- [ ] `ci.yml`: checkout → JDK 21 → build common-lib → per-module unit tests → fail on any test failure
- [ ] Cache Maven (`~/.m2`) keyed on `pom.xml` hashes — show the build-time drop in the PR
- [ ] Matrix job per service so a failure names the service
- [ ] `Dockerfile` per service: multi-stage, distroless or `eclipse-temurin:21-jre-alpine`, non-root user, read-only-friendly, `HEALTHCHECK`
- [ ] `.dockerignore` excluding `target/`, `.git/`, `.run/`
- [ ] Build and push images to GHCR tagged with `${{ github.sha }}` — never `latest` only
- [ ] Branch protection: required checks, no direct pushes to `main`
- [ ] Optional: one self-hosted runner, with a written note on why (cost) and what you hardened (ephemeral workspace, no secrets on disk)

**Done when:** the badge is green, GHCR has a sha-tagged image, and a deliberately broken test blocks the merge.

---

## Phase 2 — Infrastructure as code

- [ ] Terraform module layout: `vpc`, `eks`, `rds`, `elasticache`, `msk`
- [ ] Remote state in S3 + DynamoDB lock (or Terraform Cloud) — never local state in git
- [ ] `terraform fmt` + `tflint` + `terraform validate` in CI
- [ ] `terraform plan` posted as a PR comment
- [ ] Same Terraform creates a **kind/k3d** profile for local so the app is not cloud-only
- [ ] Ephemeral workflow: `apply` → deploy → screenshot → `destroy`, same day
- [ ] Cost recorded in README: "48h EKS run ≈ $11"

**Done when:** `terraform destroy` leaves zero billable resources, and you can show the bill.

**Why:** an always-on cluster is a hobby. Create-and-destroy is engineering.

---

## Phase 3 — GitOps

- [ ] Install ArgoCD; app-of-apps pointing at `k8s/`
- [ ] Move image tags out of `latest` into per-environment overlays (kustomize)
- [ ] Promotion flow: merge → CI pushes sha tag → bot commits tag bump → Argo syncs
- [ ] Drift demo: `kubectl edit` a deployment, screenshot Argo reverting it
- [ ] Rollback demo: `argocd app rollback`, screenshot healthy state restored
- [ ] Sync waves so Postgres/Redis/Kafka come up before services

**Done when:** you can screenshot a Git-commit-driven deploy and a rollback.

---

## Phase 4 — Observability

Current state: Prometheus + Grafana + Zipkin. That is not the stack you listed.

- [ ] Add OpenTelemetry Collector (agent DaemonSet + gateway Deployment)
- [ ] Export traces from all 5 business services over OTLP instead of direct Zipkin
- [ ] Backend: Tempo (or Jaeger) for traces
- [ ] Backend: Loki + Promtail for logs
- [ ] Single Grafana with Prometheus + Loki + Tempo datasources
- [ ] Inject `traceId`/`spanId` into log MDC via Micrometer — logs must be trace-correlated
- [ ] Exemplars from Prometheus histograms to traces
- [ ] Dashboard 1 (RED): request rate, error rate, duration p50/p95/p99 per service
- [ ] Dashboard 2 (domain): payment success rate, `AUTHORIZING` age, outbox lag, webhook DLQ depth, Kafka consumer lag, Redis lock contention
- [ ] Dashboard 3 (JVM): heap, GC pause, threads, Hikari pool saturation
- [ ] Alert rules: outbox lag > 60s, webhook DLQ > 0 for 5m, 5xx rate > 1%, p95 > 500ms
- [ ] One written walkthrough in `docs/observability.md`: "a slow payment, traced gateway → payment → vault, with the log line that explains it"

**Done when:** you can go from a red panel → trace → the exact log line in under a minute, and you have screenshots.

---

## Phase 5 — Supply chain security

- [ ] Trivy image scan in CI, fail on HIGH/CRITICAL
- [ ] `mvn dependency-check` (OWASP) or Dependabot for CVEs
- [ ] SBOM generated per image (Syft / CycloneDX), published as an artifact
- [ ] Sign images with cosign; verify in the deploy step
- [ ] K8s hardening: `runAsNonRoot`, `readOnlyRootFilesystem`, `drop: [ALL]`, `seccompProfile: RuntimeDefault`
- [ ] NetworkPolicies: services talk only to their declared dependencies
- [ ] External Secrets Operator (or SOPS + age) so no secret ever lives in git again
- [ ] Secrets in AWS Secrets Manager / SSM, synced by ESO

**Done when:** `kubectl get networkpolicy` is non-empty and no secret exists in the repo.

---

## Phase 6 — SRE practice

- [ ] Define SLOs: payment authorization success 99.9%, p95 < 300ms, webhook delivery < 60s
- [ ] Error budget + burn-rate alerts (multi-window: 1h fast burn, 6h slow burn)
- [ ] Graceful shutdown: `preStop` sleep, `terminationGracePeriodSeconds`, in-flight drain — prove zero failed requests during rollout
- [ ] HPA on CPU + custom metric (Kafka lag or RPS)
- [ ] PodDisruptionBudget per service
- [ ] Resource requests/limits for every container
- [ ] Readiness/liveness/startup probes on every service
- [ ] Chaos runs, each with a screenshot:
  - [ ] kill `vault-service` mid-authorization → compensation fires, no orphan rows
  - [ ] kill `payment-service` with in-flight requests → zero 5xx at the gateway
  - [ ] pause Postgres 30s → circuit breaker opens, recovers, no duplicate charges
  - [ ] fill webhook DLQ → alert fires
- [ ] `RUNBOOK.md`: what to do when outbox stalls, DLQ fills, Redis is down, Kafka lag spikes

**Done when:** a chaos run has a before/after dashboard screenshot and a runbook entry.

---

## Phase 7 — Evidence and writing

- [ ] `docs/evidence/` with dated screenshots: CI green, Argo sync, Grafana dashboards, trace walkthrough, chaos run, load test
- [ ] `DESIGN.md`: the 5 failure modes designed for and the alternatives rejected
- [ ] `POSTMORTEM.md`: the real bugs you fixed — vault Feign polymorphic deserialization, `ack-mode: manual`, Kafka topic-key mismatch, MapStruct null mapping, `double` fee math. Format: symptom → blast radius → root cause → fix → regression test
- [ ] `README.md`: architecture diagram, one-command run, measured numbers, cost of the ephemeral run
- [ ] ADRs in `docs/adr/` — 6 to 8 short ones (see `ENGINEERING_BACKLOG.md`)

**Done when:** someone can evaluate the project in 5 minutes without running it.

---

## Cut list — do not do these

- [~] Jenkins **and** GitLab CI as repo artifacts. Interview talking point only
- [~] Ansible. No natural fit in a container world; skip rather than fake
- [~] "I know LVM" as a resume line. Keep it as an anecdote
- [~] GKE. You chose EKS; do not split effort
- [~] Any tool whose problem you cannot name in one sentence
- [~] Any claim you cannot screenshot

---

## Budget guardrails

| Item | Cost | Rule |
|---|---|---|
| EKS control plane | ~$73/mo | Only during a proof run |
| 3× t3.large | ~$150/mo | Spot, destroyed same day |
| RDS + ElastiCache + MSK | ~$260/mo | Use smallest classes; MSK is the expensive one |
| NAT gateway | ~$35/mo | Single NAT is fine for a demo |
| **48-hour proof run** | **~$11** | `terraform destroy` is part of the demo |

Landing page for the whole thing: **"I ran this for 48 hours on EKS for $11, load-tested it, then destroyed it."** That single sentence beats a permanently running cluster.

---

## Execution order

```
Phase 0  →  Phase 1  →  Phase 7 (start writing)  →  Phase 4  →  Phase 6  →  Phase 2  →  Phase 3  →  Phase 5
```

Rationale: credibility and CI are cheap and unblock everything. Observability before infra, because you need to see the system before you scale it. Security last, because it is the least differentiated for the effort.

---

## Related

- `ROADMAP.md` — **start here**: unified order, schedule, and scoring
- `ENGINEERING_BACKLOG.md` — fintech correctness, testing rigor, and performance work
- `CHANGELOG.md` — history
- `FEATURES.md` — feature inventory
