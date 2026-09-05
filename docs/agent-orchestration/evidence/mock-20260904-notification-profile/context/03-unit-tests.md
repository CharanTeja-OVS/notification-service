# Unit-Test Handoff

- **From**: Unit-test generation agent
- **To**: Tester and validation agent
- **Status**: READY

## Test strategy

Add observable behavior tests for source-system routing, case-insensitive source lookup, explicit channel enablement, unknown-source fail-closed behavior, independent source/channel quotas, urgent post-commit dispatch, queue transitions, retry recovery, duplicate idempotency, security, correlation, and structured errors.

## Reference tests

- `FullCoverageTest`: routing, lifecycle, scheduler, failure, configuration, and source-policy behavior.
- `ResilientNotificationSenderTest`: retry, rate-limit, and independent source/channel quota behavior.
- `NotificationServiceApplicationTests`: HTTP security, correlation, validation, and malformed request behavior.

## Required evidence

Run focused tests first, then clean full tests and coverage. Any failure must include reproduction and root cause.
