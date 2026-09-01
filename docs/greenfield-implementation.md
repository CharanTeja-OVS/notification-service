# Greenfield Implementation

## Scope

This is the original implementation of the notification service as a new application. The design assumes a clean domain model, persistence-first workflow, and a pluggable delivery abstraction that can later be upgraded for Kafka, RabbitMQ, or database-backed archive processing.

## Design goals

The greenfield design intentionally covers the core operational requirements of a notification platform:

- resilient outbound sending under rate limits
- idempotent request handling
- durable status tracking for each notification lifecycle
- asynchronous processing for non-blocking delivery work
- transactional boundaries for state changes and persistence
- retry scheduling for failed messages
- immediate direct handling of urgent notifications without queueing
- deduplication through a unique domain key
- observability through Actuator and Prometheus
- explicit queued-to-delivered finalization for recovery-safe status transitions
- controller-advice-based error handling and validation normalization

## Architecture

The greenfield service is structured around a single persisted notification aggregate with attached recipients, delivery attempts, and audit events.

### Core flow

1. A client submits a notification via the REST API.
2. The application checks the request idempotency key before creating a new record.
3. The notification is stored with status `ACCEPTED`.
4. The service resolves the policy using the request `sourceSystem`, then selects only explicitly enabled channels using recipient preference and configured routing rules.
5. The notification status is advanced to `ROUTED`.
6. For urgent notifications (`CRITICAL`, `HIGH`), processing is triggered immediately.
7. Lower-priority notifications remain in persisted state and are picked up by the scheduler.
8. The delivery worker creates attempts, sets `PROCESSING`, and persists `QUEUED` state for the notification.
9. The scheduler finalizes queued work as `DELIVERED` after successful completion.
10. A retry loop re-processes failed or stuck notifications until they succeed or are exhausted.

## Requirement alignment

| Requirement | Greenfield implementation |
|---|---|
| Resiliency | `ResilientNotificationSender` wraps delivery attempts with retry logic and rate limiting |
| Actuators | Spring Boot Actuator endpoints are exposed in `application.yml` for health, metrics, env, configprops, and Prometheus |
| Rate limiter | `NotificationRateLimiter` applies an independent fixed-window quota for each configured source-system and channel pair before sending |
| Idempotency | `idempotencyKey` is required and checked before persistence; duplicate submissions return a 409 Conflict instead of silently reusing prior work |
| Status maintenance | Urgent notifications flow through `ACCEPTED -> ROUTED -> PROCESSING -> DELIVERED`; non-urgent notifications flow through `ACCEPTED -> ROUTED -> PROCESSING -> QUEUED -> DELIVERED`, with explicit recovery via `FAILED` and retry re-entry |
| Async processing | `DeliveryProcessorService.process` is annotated with `@Async` |
| Transactional boundaries | `NotificationService.submit` and `DeliveryProcessorService.process` are transactional |
| Retrying failed messages | `NotificationScheduler.retryFailedNotifications()` re-attempts failed/processing notifications |
| Saving and immediate critical processing | `NotificationService.submit()` persists immediately and invokes delivery processing for urgent notifications |
| Scheduler | `@EnableScheduling` + scheduled jobs handle deferred, queued-finalization, and retry processing |
| Deduplication | Unique `idempotency_key` index and duplicate-submission return logic |
| Controller error handling | `@RestControllerAdvice` standardizes validation and runtime error responses |

## Persist-first design

The service treats persistence as the system of record. A notification is written before any downstream delivery logic is attempted. This ensures that:

- duplicate requests do not produce duplicate notifications
- retries can resume safely after restart or failure
- pending and failed work can be recovered from the database

This is important for a clean greenfield system because the delivery layer is intentionally decoupled from the domain model.

## Components

### Notification domain model

The `Notification` aggregate stores:

- notification identity
- source and correlation metadata
- severity, priority, and type
- routing state and status
- recipients
- delivery attempts
- audit events
- idempotency key

Each notification has a unique idempotency key. The database schema includes a unique index on `idempotency_key` to prevent duplicate writes.

### Routing strategy

The routing layer resolves delivery channels from the request's source-system policy, then considers:

- recipient preferred channels
- severity override rules
- default channel configuration for that source system

The effective configuration in `application.yml` is:

```yaml
notification:
  source-systems:
    portal:
      routing:
        severity-channel-overrides:
          CRITICAL:
            - EMAIL
          HIGH:
            - EMAIL
        default-channels:
          - EMAIL
      channels:
        email:
          enabled: true
          rate-limit-per-minute: 100
          rate-limit-window-seconds: 60
```

This ensures urgent notifications prioritize email delivery while preserving a simple default path for normal messages.

### Delivery processor

`DeliveryProcessorService` is the execution engine for outbound processing. It:

- loads the notification with recipients using a fetch graph to avoid lazy-load issues
- sets the status to `PROCESSING`
- resolves channel targets
- creates `DeliveryAttempt` records
- saves them in the same transaction
- sets the notification to `QUEUED` as the persisted queued state
- marks the notification as `DELIVERED` when the queued processing path completes successfully
- marks it as `FAILED` when execution breaks

Because the work is asynchronous and transactional, it remains recoverable even under transient failures.

### Retry and scheduler design

The scheduler is responsible for two flows:

- process pending non-urgent notifications
- retry failed or stuck notifications

This gives the service a safe two-stage policy:

- urgent messages are processed immediately
- non-urgent messages wait for scheduled handling
- failed messages are retried automatically after they are persisted again

### Resilience layer

`ResilientNotificationSender` is the operational safety net. It applies:

- rate-limit checks before sending
- bounded retry attempts
- short delay-based backoff
- failure propagation with logs and structured status updates

The implementation also guards against Hibernate lazy-load failures by loading the notification graph before the async processor touches recipient collections. This ensures safe execution even when processing runs in a separate thread.

## Operational readiness

The greenfield implementation includes:

- Spring Boot Actuator exposure
- Micrometer Prometheus metrics support
- health probes and config introspection
- H2 persistence for local readiness
- scheduling to drive deferred and retry work

This makes the system production-oriented from day one while still being simple enough for a clean start.
