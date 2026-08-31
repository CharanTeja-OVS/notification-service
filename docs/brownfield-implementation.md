# Brownfield Implementation

## Scope

This is the integration-oriented design for adapting the notification service to an existing application footprint without rewriting the business contract or operational assumptions. The brownfield model emphasizes compatibility, lower risk, and stepwise adoption of the same notification processing semantics.

## Design goals

The brownfield design preserves the application’s existing operating model while still introducing the production-quality behaviors the assessment calls for:

- add resilience to outbound sending without replacing the caller contract
- support persisted, retriable notification workflows in an existing platform
- keep the API and application state compatible with current dependencies
- allow legacy or existing queue/database patterns to coexist with the new routing abstraction while keeping urgent traffic out of the queue
- provide a strict deduplication strategy to prevent duplicate notifications from replays or retries
- add scheduling and recovery without disruptive migration of core flows
- preserve a stable operational status model even when integrating with legacy infrastructure
- keep routed and queued states visible for downstream support and audit teams

## Brownfield strategy

The brownfield implementation uses the same notification model and API surface but layers in more robust operational behavior behind the service boundary.

### Integration model

1. An existing application or existing workflow calls the notification API.
2. The request is processed through the same idempotent service layer.
3. The system stores the notification and state transitions in the application database.
4. Urgent notifications are sent immediately through the resilient outbound sender.
5. Lower-priority notifications are queued for a scheduler-driven processing cycle.
6. The queued state is finalized as `DELIVERED` only after the processing path completes successfully.
7. Failed or stuck notifications are re-attempted on a retry schedule.
8. Existing infrastructure can be hooked to Kafka, RabbitMQ, or a database-backed archive without changing the public contract.

## Requirement alignment

| Requirement | Brownfield implementation |
|---|---|
| Resiliency | Keep the existing endpoint and data model, but wrap outbound operations in `ResilientNotificationSender` with retry and rate-limit guardrails |
| Actuators | Expose management endpoints to observe runtime health and throughput in a live platform |
| Rate limiter | Add a bounded windowed limiter at the delivery boundary to prevent runaway dispatch |
| Idempotency | Use `idempotencyKey` as the compatibility-safe deduplication boundary, enforced at persistence and API level with HTTP 409 on repeat submissions |
| Status maintenance | Preserve lifecycle progression in the database so an existing app can inspect notification state without custom process state, while keeping urgent messages direct and non-urgent messages queued |
| Async where required | Use asynchronous processing in the delivery pipeline while leaving caller semantics intact |
| Transactional where required | Persist domain changes and immediate status updates within a transaction to preserve consistency |
| Retrying failed messages | Add scheduler-driven recovery for `FAILED` and `PROCESSING` states |
| Saving and publishing critical immediately | Persist first and then process urgent items immediately without requiring a rewrite of current integrations |
| Scheduler | Add scheduled job execution on top of an existing service rather than replacing the application message flow |
| Deduplication | Keep duplicate request detection at the notification boundary so retries from older clients do not create duplicates |

## Why this is a brownfield fit

A brownfield system usually cannot tolerate a wholesale rewrite. The aim is to introduce the required reliability without requiring a major application restructuring. The service keeps compatibility with existing APIs and worker patterns while enhancing operational behavior.

### Key compatibility features

- same notification request contract
- same persistence-first lifecycle
- same route resolution model
- same deduplication boundary via `idempotencyKey`
- same controller surface, but enriched with error handling and lifecycle observability

This allows a production system to add reliability gradually rather than committing to a disruptive replatforming event.

## Status model usefulness in brownfield integration

The status flow is especially valuable when integrating into a legacy app because it gives operational teams a concrete view of:

- request accepted
- routing decision made
- processing started
- queued for outbound system
- failed or retried
- delivered

This status model is persisted and queryable, which makes troubleshooting and audit easier than an in-memory-only flow.

## Integration concerns handled

### Request duplication

In brownfield scenarios, duplicate submissions often happen because of retries, network issues, or upstream replay. The service prevents this with a unique database index and a same-key lookup before creating a new record.

### Rate control

Many existing systems send more notifications than the platform can safely dispatch. The rate limiter gates dispatch at the delivery edge so the system does not flood downstream channels or violate contract limits.

### Recovery from transient failures

The scheduler retries failed and stuck notifications in a safe, persisted manner. This is crucial in brownfield deployments where downstream systems may be temporarily unavailable but the application must remain functional.

### Queued-to-delivered lifecycle in brownfield mode

The same lifecycle behavior used in the clean implementation applies when integrating into an existing platform:

1. request accepted and persisted
2. status moves to routed and then processing
3. queued state is saved for deferred or asynchronous completion
4. the queued processing path is picked up and updated to delivered only after the outbound work completes successfully
5. failure states trigger retry scheduling and recovery

This gives a consistent operational model while keeping the integration low-risk and backwards-compatible.

### Operational visibility

The addition of Actuator, metrics, and request/route logging gives the operational team a window into live service health and throughput without introducing a separate monitoring system.

## Brownfield operational recommendation

For existing application landscapes, the most compatible rollout is:

1. adopt the service layer and database-backed notification lifecycle
2. keep the public API contract stable
3. enable scheduler-based retries and queue processing
4. operate urgent events immediately through the resilient sender
5. connect to Kafka/RabbitMQ or a database archive behind the abstract publisher boundary as the environment matures

That approach gives business continuity while gradually improving reliability and observability.
