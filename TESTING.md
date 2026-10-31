# TESTING.md

Testing standard for NeoPay. Each service is an independent Maven project; there is
no aggregator POM. Every command below is therefore run with `-f <module>/pom.xml`.

Current state: the full pipeline (slices, layering, coverage gate, mutation gate) is
implemented and green for `common-lib`. The same pipeline is rolled out service by
service.

## Test pyramid

```
                    e2e / full flow        (deferred: scripts/e2e-full-flow.sh)
                 integration + Testcontainers   PaymentFlow, Refund, Idempotency, EventSourcing
              slice tests (@WebMvcTest, @DataJpaTest)   per controller / repository
           unit tests (JUnit 6 + Mockito + AssertJ)     domain, services, adapters
     property tests (jqwik) + mutation gate (PIT)       pure domain logic
```

Rule: use the narrowest layer that still proves the behaviour.

| Layer | Annotation / tool | When |
|---|---|---|
| Unit | plain JUnit 6 + Mockito | business logic with no framework involvement |
| Web slice | `@WebMvcTest` + `MockMvc` | controller mapping, validation, problem details |
| Data slice | `@DataJpaTest` + Testcontainers | queries, constraints, optimistic locking |
| Integration | `@SpringBootTest` + Testcontainers | cross-layer flows against real Postgres/Kafka |
| Contract | Pact | only when an external consumer exists |
| Property | jqwik `@Property` | pure functions with invariants |
| Mutation | PIT | assertion strength of a package |

## Skill mapping

| Skill | Rule applied | Where it shows |
|---|---|---|
| `spring-boot-testing` | narrowest slice per concern; AssertJ assertions; `@MockitoBean` over `@MockBean` | slice selection above |
| `java-testing-advanced` | Testcontainers for real infrastructure; PIT for assertion strength; jqwik for properties; Pact for contracts | POM executions, property tests |
| `springboot-tdd` | red-green-refactor; coverage ratchet; test data builders | test additions in each phase |

## How to run each layer

```bash
# unit + property tests for one service
mvn -f common-lib/pom.xml test

# full gate: tests, ArchUnit, PMD, SpotBugs, ErrorProne, JaCoCo report + check
mvn -f common-lib/pom.xml verify

# layered coverage gate (reads target/site/jacoco/jacoco.csv)
python3 scripts/coverage-gate.py                 # every configured module
python3 scripts/coverage-gate.py common-lib      # one module

# mutation: 80% gate on the critical classes
mvn -f common-lib/pom.xml org.pitest:pitest-maven:mutationCoverage@critical-gate

# mutation: package-wide baseline with a 76% ratchet
mvn -f common-lib/pom.xml org.pitest:pitest-maven:mutationCoverage@full-baseline

# single test class
mvn -f common-lib/pom.xml test -Dtest='MoneyTest,FixedWindowRateLimiterTest'

# fast inner loop: every project in parallel, Testcontainers suites excluded
./scripts/test-all.sh

# full validation, including @Tag("integration") suites
./scripts/test-all.sh --full

# one project only
./scripts/test-all.sh --only payment-service

Measured on the development machine (2026-10-26):

| Mode | Wall time | Tests | Notes |
|---|---:|---:|---|
| Serial, all suites | 391s | 523 | `mvn test` per project, one after another |
| Parallel, unit only (`test-all.sh`) | 156s | 509 | 4 projects at a time, `@Tag("integration")` excluded |
| Parallel, all suites | 1061s | 523 | **do not use**  -  six concurrent builds plus Docker contend |

`--full` therefore runs serially. Integration tests are not a good parallel
workload on a single machine; they are parallelised in CI by the job matrix instead.

# all services
./scripts/verify-all.sh
```

Reports: `target/site/jacoco/index.html`, `target/pit-reports/index.html`,
`target/surefire-reports/`.

## Thresholds

Coverage is enforced twice: a bundle floor inside `mvn verify` (JaCoCo `check`),
and layer floors in `scripts/coverage-gate.py` because JaCoCo 0.8.12 silently
ignores `includes` on `BUNDLE` rules.

`common-lib` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 84% | 78% | 76% (`full-baseline`) |
| domain (`entity`, `money`, `ratelimit`, `iso20022`) | 91% | 82% |  -  |
| application (`idempotency`) | 82% | 83% |  -  |
| api (`web`, `exception`) | 83% | 85% |  -  |
| infrastructure (`audit`, `cache`, `context`, `util`, `domain`) | 81% | 76% |  -  |
| critical classes (`Money`, `FixedWindowRateLimiter`, `RedisIdempotencyStore`) |  -  |  -  | 80% |

`merchant-service` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 99% | 97% | 99% (`mutation-baseline`) |
| domain (`entity`) | 99% | 99% |  -  |
| application (`service`, `impl`, `risk`) | 99% | 96% |  -  |
| api (`controller`, `api`) | 99% |  -  |  -  |
| infrastructure (`security`, `repository`) | 99% |  -  |  -  |

`merchant-service` mutation command:

```bash
mvn -f merchant-service/pom.xml org.pitest:pitest-maven:mutationCoverage@mutation-baseline
```

`vault-service` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 99% | 99% | 99% (`mutation-baseline`) |
| application (`service`, `impl`) | 99% | 99% |  -  |
| api (`controller`) | 99% |  -  |  -  |
| infrastructure (`processor`, `validation`, `logging`) | 99% | 99% |  -  |

`payment-service` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 84% | 69% | 90% (`mutation-baseline`, Testcontainers excluded) |
| domain (`statemachine`, `ledger`, `domain`) | 90% | 79% |  -  |
| application (`service`, `impl`, `saga`, `dispute`, `fx`, `risk`) | 87% | 70% |  -  |
| api (`controller`, `api`) | 99% |  -  |  -  |
| infrastructure (`outbox`, `client`, `gateway`, `adapter`, `processor`, `repository`, `metrics`, `report`, `simulator`, `eventsourcing`, `strategy`, `simulation`) | 77% | 62% |  -  |

Mutation commands:

```bash
mvn -f vault-service/pom.xml org.pitest:pitest-maven:mutationCoverage@mutation-baseline
mvn -f payment-service/pom.xml org.pitest:pitest-maven:mutationCoverage@mutation-baseline
```

`operations-service` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 91% | 83% | 80% (`mutation-baseline`) |
| domain (`settlement`, `iso20022`, `entity`) | 83% | 74% |  -  |
| application (`recon`, `payout`) | 95% | 81% |  -  |
| infrastructure (`webhook`, `outbox`) | 94% | 90% |  -  |

`api-gateway-service` (ratcheted 2026-10-26, never lowered):

| Scope | Line | Branch | Mutation |
|---|---:|---:|---:|
| bundle | 87% | 77% | 78% (`mutation-baseline`) |
| security (`security`, `client`) | 87% | 77% |  -  |

Identity headers (`X-Merchant-Id`, `X-Key-Id`, `X-Environment`, `X-User-Role`) are
stripped from inbound requests by `HeaderAugmentingRequestWrapper` and can only be
set by an authenticated gateway handler. `HeaderAugmentingRequestWrapperTest`
covers this; do not relax it.

`discovery-service` and `config-service` contain one application class each and no
unit-testable logic. Their single `@SpringBootTest contextLoads` case is the intended
coverage; no coverage gate is configured, because a threshold there would only
measure Spring's own wiring.

Mutation commands:

```bash
mvn -f operations-service/pom.xml org.pitest:pitest-maven:mutationCoverage@mutation-baseline
mvn -f api-gateway-service/pom.xml org.pitest:pitest-maven:mutationCoverage@mutation-baseline
```

Tier targets: domain 90+, application 80+, API 70-80+, infrastructure 60+, with
branch coverage tracked separately. A floor is raised when a service improves; it
is never lowered to make a build pass.

## Conventions

- New behaviour arrives with a unit test; framework boundaries get a slice test.
- Assertions must be able to fail. A test that only executes a line is a defect.
- Prefer exact-value assertions over `isNotNull()`; PIT surviving mutants are the
  signal that an assertion is too weak.
- Property tests belong to pure functions: money arithmetic, rounding, state
  transitions, parsers.
- Contract tests are added only when a consumer outside the deployable exists.
- Do not delete a test in the same commit that replaces it.
