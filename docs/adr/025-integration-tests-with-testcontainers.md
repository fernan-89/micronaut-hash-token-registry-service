# ADR-025: Integration tests against a real MongoDB with Testcontainers

## Status
Accepted. First service to adopt it; the other services follow one repository at a time. Mirrors
thinklab-service-kit ADR-004.

## Context
Every persistence test in this service mocked the Micronaut Data repositories, so the queries Micronaut
Data generates at compile time, the BSON mapping of UUID ids, `@Version` optimistic locking and the
declared indexes were never exercised before reaching a live stack. The platform's history of live-found
persistence bugs (UUID codecs, POJO codec visibility, a transactional outbox append that failed on every
call) all sit in that gap.

## Decision
- A separate `integrationTest` suite (`src/integrationTest`) runs the adapters in a real Micronaut context
  against `mongo:7.0` started by Testcontainers 2.x as a **single-node replica set**
  (`MongoDBContainer.withReplicaSet()`), the topology the local stack uses.
- `./gradlew check` runs both suites, so CI (GitHub-hosted runners have Docker) always runs it;
  `./gradlew test` stays Docker-free.
- The 100% line/branch gate stays on the unit suite only; integration tests add confidence against real
  infrastructure, not coverage.
- Unit-test contexts set `thinklab.mongo.create-indexes: false` because they have no MongoDB.

## Consequences
- The first run of this suite found that the `@Indexes` on `HashTokenEntity` and `HashAuditEntity` had
  never been created: Micronaut Data MongoDB does not create them. `thinklab-service-kit` 0.5.0 now does
  (kit ADR-005), and `HashPersistenceIT` asserts they exist.
- Contributors need Docker to run `check` locally; `test` alone does not.
