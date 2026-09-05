# Requirements Handoff

- **From**: Requirements agent
- **To**: Architecture agent
- **Status**: READY

## Scenario

Ambiguous brownfield enhancement: evolve an existing notification implementation while preserving its usable API and adding source-system-owned policies.

## Requirements

- `REQ-001`: accept and persist notification identity, source system, correlation metadata, severity, priority, recipients, channels, and schedule fields.
- `REQ-002`: route by source-system policy, recipient preference, severity overrides, and source defaults.
- `REQ-003`: enforce channel enablement and rate limits independently per source system.
- `REQ-004`: process urgent work after commit without queueing; defer lower-priority work durably.
- `REQ-005`: provide idempotency, bounded retry, failure recovery, audit, security, observability, and status retrieval.
- `REQ-006`: preserve brownfield compatibility and document greenfield and ambiguity decisions.

## Decisions

- `DEC-001` source-system policy is the configuration owner, not application-global channel state.
- `DEC-002` duplicate idempotency submissions are explicit conflicts.
- `DEC-003` urgent means `HIGH` or `CRITICAL`; all other severities are deferred unless requirements change.

## Handoff validation

Each requirement is testable and mapped to architecture, implementation, tests, and documentation in later handoffs.
