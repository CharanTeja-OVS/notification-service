# Agentic Orchestration Evidence

This directory stores durable execution references for the generic agentic orchestrator. The orchestrator itself is generic; the notification-service artifacts below are one mock target profile.

## Mock run

- **Run ID**: `mock-20260904-notification-profile`
- **Scenario**: ambiguous brownfield enhancement
- **Target**: notification-service reference implementation
- **Result**: mock workflow completed through production-readiness review
- **State**: [state.md](evidence/mock-20260904-notification-profile/state.md)
- **Requirements handoff**: [00-requirements.md](evidence/mock-20260904-notification-profile/context/00-requirements.md)
- **Architecture handoff**: [01-architecture.md](evidence/mock-20260904-notification-profile/context/01-architecture.md)
- **Developer handoff**: [02-developer.md](evidence/mock-20260904-notification-profile/context/02-developer.md)
- **Unit-test handoff**: [03-unit-tests.md](evidence/mock-20260904-notification-profile/context/03-unit-tests.md)
- **Validation handoff**: [04-validation.md](evidence/mock-20260904-notification-profile/context/04-validation.md)
- **Documentation handoff**: [05-documentation.md](evidence/mock-20260904-notification-profile/context/05-documentation.md)
- **Production-readiness handoff**: [06-production-readiness.md](evidence/mock-20260904-notification-profile/context/06-production-readiness.md)

The evidence records the complete context passed between stages, accepted and rejected decisions, implementation preferences, validation commands, and residual risks. It is intentionally separate from the reusable agent definitions under `.github`.

## Reconciliation result

A fresh generic brownfield dry run with ID `reconciliation-mock` completed all seven stages and created seven context handoffs plus a state file. A fresh live implementation run also passed `./gradlew clean test --no-daemon --console=plain` with line coverage 98.82%, branch coverage 88.21%, and method coverage 99.20%.

The mock and live implementation align for source-system routing, explicit channel enablement, isolated source/channel limits, urgent post-commit dispatch, idempotency, security/correlation handling, and scheduler recovery. They do not align on deferred sender execution, runtime failure classification, complete audit events, or recipient/channel status detail; those remain tracked as `GAP-001` through `GAP-004` in the drift review.
