## What changed and why

<!-- The problem this solves, or the capability it adds. Link an issue if one exists. -->

## How to review

<!-- Which files/flows matter most; anything reviewers should specifically stress-test. -->

## Testing

- [ ] `mvn test` passes for every service touched
- [ ] `mvn verify` (Testcontainers) passes where applicable
- [ ] Manually exercised the affected flow via `docker compose up` (describe below if relevant)

## Contract changes

- [ ] No Kafka topic/event schema changes
- [ ] Kafka topic/event schema changed — every consuming service updated in this PR, or a
      follow-up PR is linked
- [ ] No breaking REST API changes
- [ ] REST API changed — OpenAPI docs regenerate correctly, and any dependent service/gateway
      route is updated

## Checklist

- [ ] New Flyway migrations are additive/backward-compatible (no destructive change without a
      separate rollout plan)
- [ ] Secrets/credentials are not hardcoded (env-var-driven, as elsewhere in the codebase)
- [ ] ADR added/updated if this changes a prior architectural decision
