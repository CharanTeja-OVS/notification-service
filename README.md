# Notification Service

A resilient, idempotent notification processing service built with Java 25 and Spring Boot 3.5.x, using Gradle as the build system. The service persists notification requests, resolves delivery channels, processes urgent messages immediately, and retries failed or delayed notifications through a scheduler-driven workflow.

## Overview

The notification service is designed for production-style reliability in both greenfield and brownfield scenarios.

## Agent orchestration

The project includes a reusable, generic natural-language implementation workflow under [.github](.github/agents/orchestrator.agent.md). It coordinates requirements, architecture, development, unit-test generation, validation, documentation, and production-readiness agents.

The coordinator persists execution state and bounded Markdown handoffs under `.agent-work/<run-id>/`, allowing each agent to receive the decisions and evidence produced by the previous agent. It supports `greenfield`, `brownfield`, and `ambiguous` scenarios. The notification-service is one reference implementation profile; project-specific decisions are supplied through each run's handoffs.

Preview the workflow without invoking an agent runtime:

```bash
.github/orchestrator.sh \
  --scenario brownfield \
  --requirement "Add a new source-system channel policy" \
  --dry-run
```

The recorded mock execution and stage-by-stage evidence are available in [docs/agent-orchestration](docs/agent-orchestration/README.md).

It provides:

- explicit idempotency enforcement using a unique `idempotencyKey`
- a 409 Conflict response when a duplicate idempotency key is submitted
- persistent notification lifecycle state tracking
- source-system-scoped routing based on recipient preference and severity rules
- immediate direct processing for `CRITICAL` and `HIGH` severity items without queueing
- deferred scheduling for lower-priority notifications
- retry processing for failed or stuck deliveries
- per-source-system, per-channel rate-limited outbound delivery
- observability via Spring Boot Actuator and Prometheus metrics
- centralized exception handling and validation responses
- JPA-backed persistence for notifications, recipients, attempts, and audit records

## Ambiguity validation and requirement confirmation

Ambiguous requirements are validated by translating business intent into executable assertions and test coverage before the behavior is accepted as final. This project applies that rule consistently for the most critical uncertain points:

- duplicate request semantics: the service must not silently reuse a prior notification when the same idempotency key is resubmitted; the implemented contract is a 409 Conflict response
- request identity semantics: `notificationId` is treated as a client-supplied business identifier, while `idempotencyKey` is the source-of-truth deduplication key
- severity-based queueing: `CRITICAL` and `HIGH` are processed immediately; lower severity values are deferred and scheduled based on due-time and retry policies
- lifecycle correctness: the persisted state must move through `ACCEPTED -> ROUTED -> PROCESSING -> QUEUED -> DELIVERED` and return to `ROUTED` for retry when work fails

These rules are validated in the test suite under [notification-service/src/test/java/com/schwab/notificationservice/FullCoverageTest.java](notification-service/src/test/java/com/schwab/notificationservice/FullCoverageTest.java) and verified through the Gradle test run.

## Recent implementation updates

The current release includes the following production hardening changes:

- upgraded the project to Java 25 and Gradle 9.1.0
- finalized the lifecycle sequence as `ACCEPTED -> ROUTED -> PROCESSING -> DELIVERED` for urgent notifications, and `ACCEPTED -> ROUTED -> PROCESSING -> QUEUED -> DELIVERED` for lower-priority work, with `FAILED` and retry recovery handled by the scheduler
- added persisted queued finalization so queued lower-priority items are moved to `DELIVERED` only after processing completes successfully
- corrected the Hibernate lazy-load issue by fetching notification recipient graphs before async delivery work
- deferred urgent asynchronous dispatch until the request transaction commits, preventing optimistic-lock failures caused by workers reading an uncommitted notification row
- implemented a controller advice to standardize validation and runtime error responses
- enforced source-system routing defaults, severity overrides, and explicit enabled-channel policies using configuration-bound rules
- preserved idempotency and duplicate suppression at the persistence layer
- added scheduler-based processing for deferred and failed work, with retry recovery and rate limiting at the send boundary

## Release and ownership reporting

For a more formal release and ownership view, see [docs/release-ownership-report.md](docs/release-ownership-report.md).
## Technology Stack

- Java 25
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA
- Hibernate
- H2 in-memory database for local dev/test
- Spring Validation
- Spring Scheduling
- Spring Actuator
- Micrometer Prometheus
- Gradle 9.1.0

## Application Architecture

The project is organized around a domain-driven model with a strong persistence boundary and a pluggable outbound publication layer.

### Primary architectural principles

1. Persist-first processing
   - the notification is recorded before delivery is attempted
2. Explicit idempotency contract
   - repeated requests with the same key are treated as duplicate submissions and return a 409 Conflict response instead of creating or reusing a prior record silently
3. Channel-aware routing
   - recipient preference and configuration drive delivery channel selection
4. Resilient delivery
  - transient send failures are retried with bounded attempts and independent rate limits per source system and channel
5. Scheduled recovery
   - deferred and failed messages are processed by background tasks
6. Auditability
   - every routing choice and delivery outcome is stored as audit or attempt data

## Runtime Flow

### API submission flow

1. A client calls `POST /api/notifications` with a `NotificationRequest`.
2. The controller validates the request body.
3. `NotificationService.submit(...)` resolves the request idempotency key and checks for an existing notification with that same key.
4. If duplicate, the service throws a conflict and the global handler converts it to HTTP 409.
5. If unique, the application creates a `Notification` and its recipients.
6. The service persists the notification and recipients.
7. Routing determines selected channels.
8. Status is updated to `ROUTED`.
9. For `CRITICAL` or `HIGH` notifications, the service registers direct delivery to begin immediately after the submission transaction commits; the event is not queued.
10. Lower-priority notifications remain persisted for the scheduler and are only processed when due.

The post-commit boundary is intentional: the asynchronous delivery worker always reads a committed notification row, avoiding concurrent updates of an uncommitted or detached entity.

### Processing lifecycle

A notification moves through the following states:

- `ACCEPTED`
- `ROUTED`
- `PROCESSING`
- `QUEUED` (only for non-urgent work)
- `DELIVERED`
- `FAILED`
- `DUPLICATE_SUPPRESSED`
- `PARTIALLY_DELIVERED`

The lifecycle is maintained in the database and supports both operational auditing and troubleshooting.

### Status transition detail

The current implementation explicitly follows this sequence:

1. Notification is persisted with `ACCEPTED`
2. Routing updates it to `ROUTED`
3. Delivery processing moves it to `PROCESSING`
4. Delivery attempts are created and persisted while the notification is queued as `QUEUED`
5. For lower-priority notifications, the scheduler later finalizes queued records as `DELIVERED` after successful processing
6. If an exception occurs during processing, the notification is set to `FAILED`
7. Scheduler-based retry logic reprocesses failed or stuck records by moving them back to `ROUTED` and invoking the delivery processor again

This gives a clear operational trail from submission to outbound processing and eventual completion.

## Core Components

### 1. API Layer

- `NotificationController`
  - REST controller for creating and fetching notifications
- `GlobalExceptionHandler`
  - centralized validation and error mapping
- `NotificationRequest`, `NotificationResponse`
  - request/response contract for callers

### 2. Domain Model

- `Notification`
  - core aggregate
- `NotificationRecipient`
  - recipient metadata and channel preference
- `NotificationStatus`
  - lifecycle state enum
- `NotificationSeverity`
  - urgency classification
- `NotificationPriority`
  - priority classification
- `ChannelType`
  - supported delivery channels
- `DeliveryAttempt`
  - per-channel delivery attempts
- `AuditEvent`
  - operational and routing audit trail

### 3. Service Layer

- `NotificationService`
  - handles submission, duplicate suppression, and status retrieval
- `NotificationRoutingService`
  - resolves channels using configured routing rules and recipient preference
- `DeliveryProcessorService`
  - processes and records delivery work asynchronously
- `NotificationScheduler`
  - handles deferred and retry processing

### 4. Delivery and Resilience Layer

- `NotificationDeliveryPublisher`
  - abstraction for outbound delivery transport
- `MessageNotificationPublisher`
  - default implementation for the demo/integration environment
- `NotificationRateLimiter`
  - prevents sending beyond configured limits
- `ResilientNotificationSender`
  - wraps delivery with retry and rate-limit behavior

### 5. Repository Layer

- `NotificationRepository`
  - lookup by id and idempotency key
- `NotificationRecipientRepository`
  - recipient persistence
- `DeliveryAttemptRepository`
  - delivery attempt persistence
- `AuditEventRepository`
  - routing and lifecycle audit data

## Routing Strategy

The routing engine evaluates the policy keyed by the request `sourceSystem`, then chooses channels using this priority order:

1. recipient preferred channels
2. severity override configuration
3. default configured channels
4. fallback channel defaults defined in code when no source policy is configured

When a source system has a policy, its channels are fail-closed: only channels explicitly configured with `enabled: true` may be selected. A source policy also owns independent limiter state for each channel, so `portal:EMAIL` quota consumption cannot throttle `portal:SMS` or `mobile:EMAIL`.

The configured route in `application.yml` is:

```yaml
notification:
  source-systems:
    portal:
      routing:
        severity-channel-overrides:
          CRITICAL: [EMAIL]
          HIGH: [EMAIL]
        default-channels: [EMAIL]
      channels:
        email:
          enabled: true
          rate-limit-per-minute: 100
          rate-limit-window-seconds: 60
```

This means `portal` urgent notifications default to email delivery while preserving recipient-level preferences when specified. Add another source-system key to define an independent platform policy and channel quota.

## Explicit status-flow examples

### Example A: `CRITICAL` request

Request:

```json
{
  "notificationId": "crit-001",
  "sourceSystem": "portal",
  "correlationId": "corr-crit-001",
  "notificationType": "INFO",
  "severity": "CRITICAL",
  "priority": "LOW",
  "recipients": [
    {
      "recipientId": "user-1",
      "contactValue": "ops@example.com",
      "preferredChannels": ["EMAIL"]
    }
  ],
  "idempotencyKey": "req-crit-001",
  "scheduledAt": "2026-08-31T05:10:59.539Z",
  "expiresAt": "2026-08-31T05:10:59.539Z"
}
```

Expected behavior:

- `POST /api/notifications` returns accepted routing state
- persisted status transitions: `ACCEPTED -> ROUTED -> PROCESSING -> DELIVERED`
- no queueing step is used for this severity

Response example:

```json
{
  "notificationId": "crit-001",
  "status": "ROUTED",
  "idempotencyKey": "req-crit-001",
  "createdAt": "2026-08-31T18:52:58.644940Z",
  "selectedChannels": ["EMAIL"]
}
```

Then a follow-up query such as `GET /api/notifications/crit-001` will return the later persisted state, which should move to `DELIVERED` after the immediate processing completes.

### Run the `CRITICAL` example

Start the service with Java 25 and provide HTTP Basic credentials when calling protected API endpoints. Spring Security logs the generated development password at startup unless you configure an application user.

```bash
export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"
./gradlew bootRun
```

In another terminal, replace the environment-variable values with the user and password configured for the running service:

```bash
export NOTIFICATION_USER=user
export NOTIFICATION_PASSWORD='your-running-service-password'

curl --request POST 'http://localhost:8081/api/notifications' \
  --user "$NOTIFICATION_USER:$NOTIFICATION_PASSWORD" \
  --header 'Content-Type: application/json' \
  --header 'X-Correlation-Id: corr-crit-001' \
  --data '{
    "notificationId": "crit-001",
    "sourceSystem": "portal",
    "correlationId": "corr-crit-001",
    "notificationType": "INFO",
    "severity": "CRITICAL",
    "priority": "URGENT",
    "recipients": [{
      "recipientId": "operations",
      "contactValue": "ops@example.com",
      "preferredChannels": ["EMAIL"]
    }],
    "idempotencyKey": "req-crit-001",
    "scheduledAt": "2026-08-31T05:10:59.539Z",
    "expiresAt": "2026-09-01T05:10:59.539Z"
  }'
```

The POST response is normally `201 Created` with `ROUTED`, because direct delivery begins after commit. Verify the completed state with:

```bash
curl --user "$NOTIFICATION_USER:$NOTIFICATION_PASSWORD" \
  'http://localhost:8081/api/notifications/crit-001'
```

### Example B: `LOW` request

Request:

```json
{
  "notificationId": "low-001",
  "sourceSystem": "portal",
  "correlationId": "corr-low-001",
  "notificationType": "INFO",
  "severity": "LOW",
  "priority": "NORMAL",
  "recipients": [
    {
      "recipientId": "user-2",
      "contactValue": "user@example.com",
      "preferredChannels": ["EMAIL"]
    }
  ],
  "idempotencyKey": "req-low-001",
  "scheduledAt": "2026-08-31T05:10:59.539Z",
  "expiresAt": "2026-08-31T05:10:59.539Z"
}
```

Expected behavior:

- `POST /api/notifications` returns accepted routing state
- persisted status transitions: `ACCEPTED -> ROUTED -> PROCESSING -> QUEUED -> DELIVERED`
- the scheduler picks up the queued notification later and finalizes it to `DELIVERED`

## Resilience and Retry Model

### Rate limiting

`NotificationRateLimiter` uses a rolling time window to enforce a local delivery quota. This protects downstream integration points from overload.

### Retry strategy

`ResilientNotificationSender` tries multiple send attempts with a short delay before failing. This is useful when a downstream broker, SMS gateway, or email provider is temporarily unavailable.

### Scheduler behavior

The scheduler periodically checks for:

- pending non-urgent notifications
- failed or processing notifications needing retry

The scheduler is intentionally persistence-driven; it loads notifications from the repository and reprocesses them without depending on transient in-memory state. This is important because the service must recover from partial delivery failures and restarts safely.

### Hibernate lazy-load fix

The implementation now uses repository-level `@EntityGraph` fetches for notifications and recipients before async processing. This avoids the Hibernate lazy initialization issue that could occur when the workflow attempted to iterate a detached notification's recipients after the transaction had closed.

## Transactional Boundaries

The service carefully separates persistence and delivery:

- `NotificationService.submit` is transactional for the initial request and persistence path
- `DeliveryProcessorService.process` is transactional when persisting delivery attempt state
- status transitions are stored to make scheduling and retries deterministic

This is the boundary that keeps the service durable and recoverable without requiring a fully event-sourced architecture.

## Async Behavior

`DeliveryProcessorService.process(...)` is marked with `@Async`, allowing the notification pipeline to handle outbound processing without blocking the API request path.

This is especially important for:

- non-urgent notification scheduling
- high-volume outbound scenarios
- retry processing without slowing client requests

## Observability and Monitoring

The service exposes Spring Boot management endpoints through `application.yml`, including:

- health
- info
- metrics
- prometheus
- env
- configprops
- loggers
- threaddump
- heapdump

This provides operational visibility into runtime health and configuration drift.

## Error Handling

`GlobalExceptionHandler` catches and maps common application errors such as:

- validation failures
- malformed JSON payloads
- constraint violations
- illegal arguments
- unexpected runtime exceptions

This ensures the API returns consistent and client-friendly responses.

## Greenfield vs Brownfield Design

The service is designed to support both a clean-sheet system and an integration into an existing production environment.

### Greenfield execution

- green field service built from the domain up
- new persistence, routing, scheduling, and delivery abstractions are introduced together
- easiest path for a fresh product team

### Brownfield execution

- add the same behavior to an existing application without replacing the calling contract
- keep legacy integrations stable while adding retry, persist-first, and scheduler-based reliability
- adapt the same service abstraction behind current infrastructure constraints

The design documentation is separated in the repo for both execution modes.

## Local Development

Run the application with Gradle:

```bash
cd /Users/charanteja/Charan_Workspace/notification-service
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

The application listens on port `8081`.

## Testing

Run the test suite:

```bash
cd /Users/charanteja/Charan_Workspace/notification-service
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test --no-daemon --console=plain
```

The project includes coverage-focused and behavior-focused tests around:

- rate limiting
- retry logic
- route selection
- scheduling
- notification idempotency
- request lifecycle behavior

## Key Files

- `build.gradle` — Gradle build configuration
- `application.yml` — runtime config, routing, and Actuator setup
- `NotificationService.java` — request handling and idempotency flow
- `NotificationScheduler.java` — dequeue and retry scheduling
- `DeliveryProcessorService.java` — processing and per-attempt state transitions
- `NotificationRoutingService.java` — route selection logic
- `ResilientNotificationSender.java` — resilience and retry wrapper
- `NotificationRateLimiter.java` — rate limiting logic
- `GlobalExceptionHandler.java` — API error handling

## Summary

This application demonstrates a modern, production-aware notification platform: persistent, resilient, idempotent, schedule-aware, and operationally observable. It satisfies the original assessment requirements and supports both a clean greenfield deployment and a measured brownfield adoption strategy.
