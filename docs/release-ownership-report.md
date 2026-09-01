# Release and Ownership Report

## Release Summary

- Product domain: notification processing platform
- Runtime: Java 25
- Framework: Spring Boot 3.5.x
- Build tool: Gradle 9.1.0
- Release status: production-ready operational baseline for greenfield and brownfield deployment
- Primary operating model: persisted-first notification lifecycle with scheduler-driven recovery and validation

## Executive Summary

The notification service has been updated to a production-oriented state aligned with the current assessment requirements. The release includes a durable notification lifecycle, routing by severity and recipient preference, resilient send behavior, retry scheduling, and operational observability.

The design preserves a clear separation between:

- request admission and idempotency
- routing and channel selection
- persistence and operational state
- asynchronous processing and finalization
- retries and operational observability

This architecture supports two deployment modes:

1. Greenfield adoption: full-system rollout with clean domain boundaries
2. Brownfield adoption: incremental integration into an existing platform with compatibility-focused safeguards

## Ownership Model

### Product / Domain ownership

Responsible for:

- notification business rules
- severity and priority semantics
- API contract and idempotency requirements
- user-facing payload expectations

### Engineering ownership

Responsible for:

- technical implementation and runtime behavior
- persistence decisions and scheduling behavior
- routing configuration and integration boundaries
- resilience and retry strategy
- production readiness and operational monitoring

### Operations / Support ownership

Responsible for:

- reviewing health, metrics, and system state
- monitoring queued, failed, and retried notifications
- validating delivery throughput and rate-limit behavior
- addressing downstream integration issues and alerts

## Release Scope

### Included in this release

- Java 25 runtime compatibility
- Gradle-based build and wrapper modernization
- notification persistence and lifecycle state tracking
- idempotent request handling
- source-system routing configuration with severity overrides, default channels, explicit channel enablement, and per-channel quotas
- immediate direct processing for `CRITICAL` and `HIGH` severity notifications without queueing
- delayed scheduler handling for lower-priority processing
- retry scheduler for `FAILED` and `PROCESSING` notifications
- queued finalization to `DELIVERED`
- rate-limited outbound send behavior
- centralized validation and error handling via controller advice
- Actuator and Prometheus metrics exposure
- greenfield and brownfield design documentation

### Explicitly out of scope for this release

- real broker integration for Kafka or RabbitMQ transport beyond the abstraction layer
- enterprise identity and access controls beyond the app-level API contract
- long-term archive retention policies for historical delivery data
- advanced SLA dashboards beyond Spring Boot metrics and actuator output

## Design Decisions and Rationale

### Persist-first model

The service records each notification before any outbound processing is attempted. This reduces risk of missing work during restart or transient outages and supports consistent retry and audit behavior.

### Idempotency as first-class contract

The `idempotencyKey` is treated as a business-safe deduplication boundary. Repeated submissions are rejected as `409 Conflict` so the caller knows a duplicate attempt was made instead of silently reusing a prior notification. This keeps the API contract explicit and avoids accidental duplicate writes from retries or replay errors.

### Severity-based routing

The routing strategy is intentionally simple and deterministic:

- the request `sourceSystem` selects the platform policy first
- recipient preference is considered next
- severity overrides determine urgent channel selection
- default channels provide a safe fallback for standard notifications

Each platform policy explicitly enables its available channels. Rate limits are isolated per `(sourceSystem, channel)`, so traffic from one platform cannot deplete another platform's delivery quota.

### Scheduler as operational recovery mechanism

The scheduler separates three operational concerns:

- immediate direct urgent processing
- delayed lower-priority processing
- retry recovery for failed or stuck notifications

This gives the service resilience and reduces dependency on in-memory state.

### Post-commit urgent dispatch

`CRITICAL` and `HIGH` delivery is registered only after the persisted notification transaction commits. The asynchronous processor therefore loads a committed row before updating `PROCESSING` and `DELIVERED`, preventing optimistic-lock failures caused by concurrent processing of an uncommitted entity. This preserves immediate urgent handling without allowing those notifications into the deferred queue.

### Queued-to-delivered finalization

The queued state is used only for lower-priority work. Urgent messages bypass the queue and are finalized as `DELIVERED` directly. This keeps the system auditable and prevents premature status completion when the processing step has not yet finished for deferred tasks.

### Resilience and rate limit guardrails

At the outbound boundary, send attempts are wrapped with source-system and channel-specific rate limiting plus retry logic to protect downstream dependencies and limit throughput spikes without cross-platform interference.

### Observability and supportability

Actuator and metrics endpoints provide runtime visibility into health, config, and operational behavior for engineering and operations staff.

## Release Readiness Checklist

- [x] Java version updated to target runtime
- [x] Gradle build and wrapper compatibility confirmed
- [x] persistence and lifecycle model implemented
- [x] retry and scheduler behavior in place
- [x] routing configuration defined and validated
- [x] validation and error handling consistent
- [x] tests passing under Java 25
- [x] architecture and migration docs updated
- [x] greenfield and brownfield scenarios documented

## Operational Notes

### Notification status flow

The service currently follows the operational sequence below:

- `ACCEPTED`
- `ROUTED`
- `PROCESSING`
- `QUEUED`
- `DELIVERED`
- `FAILED` with retry recovery

This model provides a clear operational trail and supports troubleshooting and audit review.

### Recovery posture

Recovery is handled via repository-backed status inspection and periodic retry scheduling rather than transient in-memory execution tracking. This is the key design decision that makes the platform resilient through restarts and partial failures.

Urgent delivery begins after the admission transaction commits, so direct processing does not race the original notification save.

### Risk profile

The main remaining risks are not code-level breakage but deployment-level concerns:

- broker transport integration beyond the abstraction layer
- downstream platform rate or quota limitations
- operational monitoring setup in a production environment
- security hardening and secret management for external transports

## Release Recommendation

This release is suitable for greenfield deployment and for staged brownfield adoption under a controlled rollout plan. It should be deployed with operational monitoring for queue depth, failed notifications, retry volume, and downstream delivery latency.

## Ownership Sign-off

- Engineering: responsible for implementation and verification
- Operations: responsible for runtime health monitoring and alert response
- Product: responsible for business rule validation and change acceptance

This release should be treated as a stable operational baseline for the current notification platform scope.
