# Contributing

## Before you start

Read [docs/index.md](docs/index.md) for the architecture and [TESTING.md](TESTING.md) for the quality gates. Build with JDK 21 and Maven; Docker is required for integration tests.

## Build and test

```bash
mvn -q -f common-lib/pom.xml -DskipTests install
mvn -f payment-service/pom.xml verify
python3 scripts/coverage-gate.py
```

`scripts/verify-all.sh` runs every module and both gates.

## Rules a pull request must satisfy

| Rule | Reason |
|---|---|
| `mvn verify` passes for every touched module | Tests, static analysis and coverage are part of done |
| Coverage and mutation floors never decrease | A floor lowered once is lost permanently |
| A new endpoint has a contract test | The OpenAPI snapshot is generated from the same source |
| A new schedule has a ShedLock lease | Two replicas must not run the job twice |
| A money path has an idempotency key | Retries must not duplicate a charge |
| A new document is linked from `docs/index.md` | One index, no orphan pages |
| No secrets in code or configuration | Use environment variables listed in `docs/REFERENCE_CONFIGURATION.md` |

## Commit messages

Conventional Commits, imperative, scoped by module:

```
feat(payment): accept signed inbound PSP webhooks
fix(settlement): reject a negative net before transfer
docs(architecture): add the container diagram
test(perf): measure chaos recovery
```

## Branching

`main` is the release branch. Work on a topic branch and open a pull request. CI runs the full 19-job matrix on every push.

## Changing a contract

1. Add the new field or endpoint without removing the old one.
2. Regenerate the OpenAPI snapshot: `mvn -f payment-service/pom.xml test -Dtest=OpenApiContractTest -Dopenapi.write=true`.
3. Describe the deprecation in `CHANGELOG.md`.
4. Update the owning document under `docs/`.

## Reporting a defect

Open an issue with the failing command, the expected result, the observed result, and the commit. For security defects follow [SECURITY.md](SECURITY.md).
