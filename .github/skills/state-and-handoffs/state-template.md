# Agent Run State

- **Run ID**: <run-id>
- **Scenario**: greenfield | brownfield | ambiguous
- **Requirement**: <requirement>
- **Started**: <timestamp>
- **Current Agent**: <agent>
- **Overall Status**: NOT_STARTED | IN_PROGRESS | BLOCKED | COMPLETE | COMPLETE_WITH_RISKS | FAILED

## Execution Status

| Order | Agent | Status | Handoff | Validation |
|---:|---|---|---|---|
| 1 | Requirements | NOT_STARTED | context/00-requirements.md | |
| 2 | Architecture | NOT_STARTED | context/01-architecture.md | |
| 3 | Developer | NOT_STARTED | context/02-developer.md | |
| 4 | Unit-test generation | NOT_STARTED | context/03-unit-tests.md | |
| 5 | Tester and validation | NOT_STARTED | context/04-validation.md | |
| 6 | Documentation | NOT_STARTED | context/05-documentation.md | |
| 7 | Production readiness | NOT_STARTED | context/06-production-readiness.md | |

## Decisions

- **DEC-001**: <decision, alternatives, rationale, owner>

## Requirement Traceability

| Requirement ID | Requirement | Design/Code Evidence | Test Evidence | Documentation Evidence | Status |
|---|---|---|---|---|---|
| REQ-001 | <requirement> | <files/symbols> | <test> | <document> | OPEN |

## Gaps

- **GAP-001**: <live implementation gap, owner, severity, mitigation, acceptance condition>

## Validation Evidence

- **Command**: <command>
- **Result**: <result>
- **Artifacts**: <paths>

## Coordinator Notes

<append-only notes about transitions, retries, rejected outputs, mock/live drift, and follow-up work>

## Model Execution Record

| Role | Provider/model | Timestamp | Tools available | Output disposition | Evidence |
|---|---|---|---|---|---|
| <role> | <identifier or undisclosed-by-policy> | <timestamp> | yes/no | accepted/modified/rejected/escalated | <handoff or validation path> |
