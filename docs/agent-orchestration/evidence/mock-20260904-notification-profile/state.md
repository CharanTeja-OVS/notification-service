# Mock Agent Run State

- **Run ID**: `mock-20260904-notification-profile`
- **Scenario**: ambiguous brownfield enhancement
- **Target**: notification-service reference profile
- **Overall status**: COMPLETE_WITH_RISKS
- **Drift review**: [07-drift-review.md](context/07-drift-review.md)

## Preferences captured

- Build system: Gradle, not Maven
- Runtime: Java 25 LTS target
- Package convention: `com.schwab.notificationservice`
- Delivery: urgent `HIGH`/`CRITICAL` direct after commit; lower severity scheduler-backed
- Routing: source-system policy owns channel enablement and severity/default routing
- Rate limiting: independent per `(sourceSystem, channel)` window
- Reliability: bounded retry, failure classification, durable recovery
- API: explicit idempotency conflict behavior and status retrieval
- Security: authenticated API, correlation IDs, safe audit data, restricted operational exposure
- Testing: focused behavior tests, clean full suite, coverage evidence, runtime smoke validation
- Documentation: greenfield, brownfield, ambiguity, decisions, ownership, limitations, and rollback

## Stage status

| Stage | Status | Evidence |
|---|---|---|
| Requirements | COMPLETE | [00-requirements.md](context/00-requirements.md) |
| Architecture | COMPLETE | [01-architecture.md](context/01-architecture.md) |
| Developer | COMPLETE | [02-developer.md](context/02-developer.md) |
| Unit-test generation | COMPLETE | [03-unit-tests.md](context/03-unit-tests.md) |
| Tester and validation | COMPLETE | [04-validation.md](context/04-validation.md) |
| Documentation | COMPLETE | [05-documentation.md](context/05-documentation.md) |
| Production readiness | COMPLETE_WITH_RISKS | [06-production-readiness.md](context/06-production-readiness.md) |
| Mock-versus-live drift review | COMPLETE | [07-drift-review.md](context/07-drift-review.md) |

## Decision register

- `DEC-001`: use source-system policy as the configuration boundary.
- `DEC-002`: fail closed for channels omitted or disabled in a configured policy.
- `DEC-003`: isolate limiter state by source and channel.
- `DEC-004`: register urgent async work after transaction commit.
- `DEC-005`: preserve idempotency as a unique business key and return conflict on duplicate submission.
- `DEC-006`: keep external transport behind an adapter for greenfield and brownfield migration.

## Validation evidence

- `bash -n .github/orchestrator.sh`: passed.
- Mock coordinator run: seven stage prompts, outputs, and context handoffs generated.
- `./gradlew clean test --no-daemon --console=plain`: passed in the reference implementation on 2026-09-04.
- JaCoCo: line 98.82% (`751/760`), branch 88.21% (`187/212`), method 99.20% (`249/251`).
- Drift review identified `GAP-001` through `GAP-004`; these remain implementation work, not accepted behavior.
- Production readiness: risks remain around real transport integration and production actuator exposure.
