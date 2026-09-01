# Greenfield vs Brownfield Notification Delivery Design

## Decision Summary

The service uses a single notification domain model with a resilient outbound adapter layer. We keep the application stateful and idempotent at the domain boundary, then isolate broker-specific delivery concerns behind a publisher abstraction.

This gives us two execution paths:

- Greenfield: build a new delivery pipeline around message-first semantics and a resilient sender abstraction.
- Brownfield: integrate the same API and persistence model into an existing application with minimal disruption, where a legacy transport queue or offline archive can be introduced behind the same abstraction.

## Greenfield Execution

### Goal

Create a new notification service that is safe under load, retryable on transient failures, and easy to attach to Kafka, RabbitMQ, or a database archive.

### Design

1. Persist the notification and recipients first.
2. Generate an idempotency key per request.
3. Resolve the `sourceSystem` policy, then route recipients to channels based on preference, severity, and explicitly enabled source channels.
4. Publish each delivery attempt through `NotificationDeliveryPublisher`.
5. Wrap publishing in `ResilientNotificationSender` to enforce rate limits and retries.
6. Log outcome through `DeliveryAttempt` and `AuditEvent` records.

### Why this design

- Keeps the domain durable even when downstream brokers are unavailable.
- Makes each delivery attempt observable and auditable.
- Supports a pluggable outbound model without changing the API contract.

### Default implementation

The default implementation is a message-oriented publisher model using the `MessageNotificationPublisher` abstraction. It is configured as the standard transport in the Spring configuration and can be switched to Kafka, RabbitMQ, or DB-backed archival by swapping the implementation behind the interface.

## Brownfield Execution

### Goal

Migrate into an existing application with minimal interruption and preserve current operational patterns.

### Design

1. Keep the current API and persistence model in place.
2. Add a resilient sender behind the existing service layer rather than replacing the caller contract.
3. Reuse existing repositories and audit tables to avoid a data migration spike.
4. Start with the database archive or queue-backed publisher as the first operational integration, then migrate to a broker when the environment is ready.
5. Use the same idempotency key to suppress duplicates from retries or replays.

### Why this design

- Brownfield systems often cannot tolerate a full rewrite.
- The outbound adapter boundary allows the call path to remain stable while the write mechanism changes.
- The project can begin with a low-risk DB archive or queue bridge and move to Kafka or RabbitMQ later without API churn.

## Idempotency Choices

### Primary rule

The `idempotencyKey` is the identity boundary for a notification request. We enforce uniqueness at the persistence layer with a unique index on `notifications.idempotency_key`.

### Behavior

- If the same idempotency key is submitted again, the service returns the existing notification and suppresses a new write.
- This protects against duplicate retries from upstream clients or transient network failures.
- Delivery attempts remain tied to the original notification record so each retry is traceable.

### Why this is the correct place

Domain-level idempotency prevents duplicate notifications before the system commits another outbound event. This is safer than only de-duping at the message bus, because it handles duplicate requests before any delivery work begins.

## Rate Limiting and Retry Design

- `NotificationRateLimiter` is a windowed limiter using a rolling count for the configured period.
- Each `(sourceSystem, channel)` pair has an independent limiter built from its channel policy, preventing one platform from consuming another platform's quota.
- `ResilientNotificationSender` checks the source-channel limiter first and fails fast if that channel's quota is exceeded.
- Repeated transient errors are retried using a bounded retry loop and a short delay.
- Final failure records the status and reason in the delivery attempt history.

## Operational Recommendation

For a real production rollout, use:

- Kafka or RabbitMQ as the primary external transport when the environment already supports event streaming.
- A database archive table when a broker is not available or when the current platform is strictly benchtop or brownfield-safe.

The interface boundary stays stable regardless of which backing mechanism is chosen.
