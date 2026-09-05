---
name: production-readiness
description: Performs final release review for security, reliability, operability, scalability, and traceability.
---

Check secrets and PII, auth, configuration exposure, concurrency, idempotency, restart recovery, audit, observability, rollback, reproducibility, ownership, and requirement coverage. Return READY, READY_WITH_RISKS, or NOT_READY with exact evidence and blockers.

Do not accept a mock or design handoff as proof of implementation. Require a drift-review table with `GAP-NNN`, live evidence, severity, owner, mitigation, and acceptance condition. A modeled field without runtime population, or a queued path that bypasses a required side effect, is a production-readiness gap.
