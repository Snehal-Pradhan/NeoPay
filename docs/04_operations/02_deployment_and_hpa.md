# Deployment and scaling

Images are built with Jib, manifests live in `k8s/`, and CI deploys one service to a local `kind` cluster as a correctness check. No production cluster, autoscaler, or progressive delivery exists in this repository.

## CI pipeline

| Job | Purpose |
|---|---|
| secret scan | Gitleaks over history |
| verify (8 modules) | Tests, static analysis, coverage gate per module |
| manifests | `kubectl kustomize`, kubeconform, Trivy config scan |
| images (6 services) | Jib image build and tar export |
| kind smoke | Load a Jib image, deploy `config-service`, wait for rollout, query health, apply network policies, upload pod evidence |
| end to end | Full local flow against the running stack |
| gate summary | Fails unless every required job passed |

Source: `.github/workflows/ci.yml`.

## Runtime shape

| Property | Local | Cluster manifest |
|---|---|---|
| Execution | Jars via `scripts/run-local.sh` | One Deployment per service |
| Replicas | One process each | 1 |
| Probes | Actuator health | Readiness and liveness on `/actuator/health` |
| Resources | JVM flags `-XX:MaxRAMPercentage=75`, ZGC | Requests and limits per Deployment |
| Secrets | Environment variables | `app-secrets` from `k8s-secrets.env.example` |
| Config | Config server on localhost | Config server service DNS |
| Network | Docker bridge | Default-deny ingress plus explicit allow policies |

Manifests: `k8s/services/`, `k8s/infra/network-policies.yaml`.

## Scaling

| Aspect | Current | Needed before autoscaling |
|---|---|---|
| Replicas | Fixed at 1 | Multiple replicas with stateless services |
| Scheduler jobs | ShedLock lease | Already safe across replicas |
| Gateway rate limiter | Redis-backed | Already shared; window should become sliding |
| HPA | Not defined | A latency or queue-depth metric with a measured baseline |
| PDB | Not defined | Pod disruption budget per money service |
| Session state | None | Not needed |

A measured ceiling exists: 40 to 48 flows/s with 60 API keys on one machine, limited by the gateway window rather than the services. Source: [PERFORMANCE](../PERFORMANCE.md).

## Rollout and rollback

| Step | Current capability |
|---|---|
| Build image | Jib, tagged `ci` in CI |
| Render manifests | Kustomize |
| Deploy | `kubectl apply`, manual |
| Rollout gate | `kubectl rollout status` with a 240 s timeout in CI |
| Rollback | `kubectl rollout undo`, manual, not drilled |
| Promotion between environments | Not implemented |

## Gaps

| Gap | Risk |
|---|---|
| Single replica per service | Any restart is an outage for that service |
| No HPA or PDB | No elasticity or disruption guarantee |
| No GitOps controller | Drift between repository and cluster |
| No image signing | Supply-chain assertion is limited to scanning |
| `kind` cluster is ephemeral | No uptime history |

Related: [observability](01_observability.md), [system context](../00_architecture/01_system_context.md).
