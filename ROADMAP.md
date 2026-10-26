# Upgrade Roadmap — Unified Plan

Single sequenced view of everything planned for this project.
Detail lives in `DEVOPS_CHECKLIST.md` and `ENGINEERING_BACKLOG.md`; this file decides **order** and **why**.

---

## The thesis

The project is **8/10 on architecture and 2/10 on proof**. That gap is the entire opportunity.

Everything below serves one sentence:

> **"I built it, I shipped it, I observed it, I broke it on purpose, and I fixed what fell out."**

No item earns credit without an artifact: a test, a number, a screenshot, or a document.

---

## Baseline scorecard

| Dimension | Now | Ceiling after this plan |
|---|---:|---:|
| Architecture | 8.0 | 9.0 |
| Verification | 2.0 | 8.5 |
| Fintech credibility | 3.0 | 8.5 |
| Operational maturity | 2.0 | 8.0 |
| Originality / scale | 3.0 | 5.0 |
| **Overall (fintech / platform roles)** | **5.0** | **8.5–9.0** |
| **Overall (FAANG SDE-1)** | **5.0** | **7.0–7.5** |

Originality and scale are the two dimensions this plan **cannot** fix. The package lineage and the 7.7k-LOC footprint are permanent facts. Everything else is addressable.

---

## Item 0 — the only actual defect

**`SettlementTransactionExecutor`: `double FEE_RATE = 0.02`**

Verified: **1 in 100 amounts produces the wrong fee**, always under-charging by 1 paisa.

| Gross | Exact 2% | Current code | Delta |
|---:|---:|---:|---:|
| ₹1.25 | 3p | 2p | −1 |
| ₹5.25 | 11p | 10p | −1 |
| ₹100.25 | 201p | 200p | −1 |
| ₹1001.25 | 2003p | 2002p | −1 |

Cause: `0.02` is not representable in binary; `25 * 0.02` evaluates to `0.49999999999999994` and rounds down. Hits every amount ≡ 25 (mod 100) paise. GST compounds it — at ₹1001.25 the merchant is overpaid by 2 paise.

**Fix:** integer basis points, `Math.multiplyExact(amount, 200) / 10_000`, explicit rounding rule, boundary tests at 1p / 25p / 10025p / `Integer.MAX_VALUE / 2`.
**Effort:** 30 minutes. **Priority:** absolute first.

---

## Unified backlog, ranked by points per hour

| # | Item | Area | Impact | Effort | Proof artifact |
|---|---|---|---:|---:|---|
| 1 | Purge + rotate committed secrets, `gitleaks` | DevOps | **1.0** | 2h | `git log --all -p` clean |
| 2 | Fix `double` fee math | Fintech | defect | 0.5h | boundary test at 4 amounts |
| 3 | CI: build, test, image, GHCR, branch protection | DevOps | **1.5** | 1d | green badge + sha-tagged image |
| 4 | Double-entry ledger + invariant checker | Fintech | **1.0** | 3d | debits == credits, imbalance rejected |
| 5 | Property tests (jqwik) + PIT mutation score | Rigor | **0.5** | 1d | published mutation score |
| 6 | OTel Collector → Prometheus + Loki + Tempo | DevOps | **1.25** | 2d | trace→log walkthrough doc |
| 7 | ADRs ×8 | Rigor | **0.4** | 0.5d | `docs/adr/` |
| 8 | Refunds: partial, idempotent, ledger reversal | Fintech | **0.5** | 2d | e2e refund test, balanced ledger |
| 9 | Reconciliation + break report | Fintech | **0.75** | 2d | recon report with 4 known breaks |
| 10 | SLOs, burn-rate alerts, chaos runs, probes, HPA | SRE | **0.75** | 2d | before/after dashboard screenshots |
| 11 | One profiled + measured performance win | Java | **0.5** | 1d | flame graph + p95 before/after |
| 12 | Terraform (VPC, EKS, RDS, ElastiCache, MSK) | DevOps | **1.0** | 2d | `destroy` leaves $0 |
| 13 | ArgoCD GitOps + promotion + rollback | DevOps | **0.75** | 1d | revert/rollback screenshots |
| 14 | Supply chain: Trivy, SBOM, cosign, NetworkPolicy, ESO | DevOps | **0.5** | 1d | non-empty `kubectl get netpol` |
| 15 | Contract tests (gateway↔merchant, payment↔vault) | Rigor | **0.3** | 1d | contract pinned on `VaultChargeResponse` |
| 16 | PCI scope test (PAN never logged/crosses boundary) | Fintech | **0.25** | 0.5d | `docs/pci-scope.md` + test |
| 17 | Explicit transition table + exhaustive tests | Fintech | **0.25** | 0.5d | 409 on illegal transition |
| 18 | Inbound webhook HMAC + replay cache | Fintech | **0.2** | 0.5d | tamper/stale/replay tests |
| 19 | OpenAPI + spec diff gate | Rigor | **0.2** | 0.5d | committed `openapi.yaml` |
| 20 | Evidence pack + POSTMORTEM + RUNBOOK + DESIGN | Writing | **1.0** | 1d | `docs/evidence/` |

**Raw sum: ~12.7 points. Realized: 8.5–9.0.** Points do not stack linearly — the last few items add polish, not score. The cap is originality and scale, not effort.

---

## Schedule

**Full-time: ~3 weeks. Part-time (15h/wk): ~6 weeks.**

### Week 1 — credibility and the ledger

- [ ] #1 secrets purge + rotation + `gitleaks`
- [ ] #2 fee math fix + boundary tests
- [ ] #3 CI pipeline + distroless images + GHCR + branch protection
- [ ] #4 ledger schema, write path, invariant checker
- [ ] #7 first four ADRs
- **Done when:** green CI badge, zero secrets in history, `sum(debit) == sum(credit)` across all tests.

### Week 2 — domain depth

- [ ] #8 refunds end-to-end with ledger reversal
- [ ] #5 property tests on state machine, Money, fee math, idempotency
- [ ] #17 transition table
- [ ] #16 PCI scope test + doc
- [ ] #7 remaining ADRs
- **Done when:** partial refund leaves the ledger balanced; mutation score published.

### Week 3 — observability

- [ ] #6 OTel Collector, Tempo, Loki, Promtail, unified Grafana
- [ ] Trace ID in log MDC; exemplars from metrics to traces
- [ ] 3 dashboards: RED, domain (outbox lag, DLQ, Kafka lag), JVM
- [ ] Alert rules; `docs/observability.md` walkthrough
- **Done when:** red panel → trace → exact log line in under a minute, with screenshots.

### Week 4 — reconciliation and SRE

- [ ] #9 recon ingest, matching, break classification, metric, alert
- [ ] #10 SLOs, error budget, burn-rate alerts, probes, HPA, PDB
- [ ] Chaos: kill vault mid-auth, kill payment with in-flight, pause Postgres
- [ ] `RUNBOOK.md`
- **Done when:** a chaos run has a before/after screenshot and a runbook entry.

### Week 5 — cloud and GitOps

- [ ] #12 Terraform modules, remote state, `plan` on PR
- [ ] Ephemeral EKS run: apply → deploy → screenshot → destroy, cost recorded
- [ ] #13 ArgoCD app-of-apps, sync waves, drift revert, rollback
- [ ] #14 Trivy, SBOM, cosign, NetworkPolicy, External Secrets
- **Done when:** `terraform destroy` leaves nothing billable and Argo rollback is screenshotted.

### Week 6 — proof and polish

- [ ] #11 async-profiler / JFR → one real bottleneck → fix → re-measure
- [ ] #15 contract tests
- [ ] #18 inbound webhook verification
- [ ] #19 OpenAPI + diff gate
- [ ] #20 `docs/evidence/`, `DESIGN.md`, `POSTMORTEM.md`, README rewrite
- **Done when:** someone can evaluate the project in 5 minutes without running it.

---

## If you only have one week

### Target: fintech / platform / startup
1. #1 secrets (2h)
2. #2 fee fix (0.5h)
3. #3 CI + images (1d)
4. #4 ledger (3d) ← the single highest-value item
5. #7 four ADRs (2h)

### Target: FAANG SDE-1
1. #1 secrets (2h)
2. #2 fee fix (0.5h)
3. #3 CI + images (1d)
4. #5 property tests + PIT (1d)
5. #20 writing: POSTMORTEM + DESIGN (1d)

Then stop and go back to DS&A. For FAANG SDE-1, this project is a tiebreaker, not the deciding factor.

---

## Evidence rules

Every item must produce **one** of:

- a test that fails without the fix and passes with it
- a number with a before/after
- a screenshot in `docs/evidence/` with a date
- a document explaining a decision and the alternatives rejected

If it produces none of those, it does not go on the resume.

---

## Cut list (consolidated)

- Jenkins / GitLab CI as repo artifacts — interview talk only
- Ansible — no natural fit
- GKE — chose EKS
- Wallet payments — no adapter, low signal
- GraphQL / gRPC for novelty
- Rewrite in another language
- More services for the count
- Any claim you cannot screenshot
- "I know LVM" as a resume line — keep as an anecdote

---

## Resume bullets this plan unlocks

Use only what you have actually completed and can defend.

> Built a 5-service payment platform (gateway, payment, merchant, vault, operations) on Spring Boot 4 / Kafka / Postgres / Redis; implemented transactional outbox, ShedLock-guarded schedulers, and DB-level idempotency for effectively-once processing under retry and replica failure.

> Designed an append-only double-entry ledger with per-transaction balance invariants and a nightly reconciliation job against simulated bank statements, classifying missing, duplicate, and amount-mismatch breaks.

> Implemented partial and idempotent refunds with ledger reversal and settlement carry-forward; verified zero orphaned rows across injected failure modes with Testcontainers.

> Operated the platform on EKS with Terraform, ArgoCD GitOps, and OpenTelemetry → Prometheus/Loki/Tempo; instrumented SLOs and burn-rate alerts, and load-tested at N concurrent users with measured p95.

> Root-caused and fixed a cross-service polymorphic deserialization failure in a Feign path, a Kafka manual-ack misconfiguration that silently exhausted retries, a silent MapStruct field drop, and an off-by-one-paisa fee calculation caused by binary floating point.

That last bullet is the strongest asset in the entire plan. It is the only one that proves you debug rather than assemble.

---

## Related

- `DEVOPS_CHECKLIST.md` — phased DevOps execution detail
- `ENGINEERING_BACKLOG.md` — fintech correctness, rigor, performance detail
- `CHANGELOG.md` — project history
- `FEATURES.md` — feature inventory
