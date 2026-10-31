# Tech stack matrix

Every row names the version actually declared in the build or the container definition.

| Layer | Choice | Version | Where declared |
|---|---|---|---|
| Language | Java | 21 | `*/pom.xml` `maven.compiler.release` |
| Framework | Spring Boot | 4.1.0 | parent POM |
| Cloud | Spring Cloud | release train from parent | `spring-cloud.version` |
| Discovery | Eureka (Spring Cloud Netflix) | from parent | `discovery-service` |
| Config | Spring Cloud Config, native profile | from parent | `config-service` |
| Gateway | Spring Cloud Gateway (WebMVC) | from parent | `api-gateway-service` |
| Web | Spring MVC, Tomcat | from Boot | all services |
| Persistence | Spring Data JPA, Hibernate | from Boot | all data services |
| Migrations | Flyway | from Boot | `src/main/resources/db/migration` |
| Database | PostgreSQL | 16 | `docker-compose.yml` |
| Cache | Redis | 7 | `docker-compose.yml` |
| Broker | Kafka (confluent-local) | 7.5.0 | `docker-compose.yml` |
| Scheduler lock | ShedLock | declared in service POMs | `payment-service/pom.xml` |
| Resilience | Resilience4j | from Cloud | `config-service/src/main/resources/config/*.yml` |
| Tracing | Micrometer Tracing, Zipkin | from Boot | `application.yaml` |
| Metrics | Micrometer, Prometheus registry | from Boot | `application.yaml` |
| Tests | JUnit 5, AssertJ, Mockito, jqwik, ArchUnit, Testcontainers | see POMs | module POMs |
| Mutation | PIT | 1.19.0 | `payment-service/pom.xml` |
| Static analysis | PMD, SpotBugs, ErrorProne | see POMs | module POMs |
| Coverage gate | JaCoCo | 0.8.12 | module POMs |
| Observability UI | Prometheus, Grafana, Zipkin | pinned images | `k8s/stateful/` |
| Images | Jib | 3.5.2 | module POMs |
| CI | GitHub Actions | 19 jobs | `.github/workflows/ci.yml` |

## Selection summary

| Concern | Why this choice fits the repository |
|---|---|
| Spring Boot 4 and Java 21 | Records, sealed interfaces and pattern matching carry the payment response models; virtual threads are available but not enabled |
| Postgres | Unique constraints and transactions carry idempotency and ledger invariants; no service needs a document store |
| Kafka | Only fan-out to webhook delivery; the write path does not depend on it |
| Redis | Rate limiting, idempotency in-progress markers, ShedLock leases; every use fails open or duplicates safely |
| Flyway | Schema is part of the artifact; the baseline dump is checked in |
| Gateway in front of services | Merchant authentication and throttling have one owner |

Related: [alternatives and trade-offs](04_tech_stack_alternatives.md).
