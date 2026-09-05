# Architecture Handoff

- **From**: Architecture agent
- **To**: Developer agent
- **Status**: READY

## Design

Use a persisted notification aggregate, routing service, source-system policy configuration, delivery processor, resilient sender, per-source/channel limiter map, scheduler, repositories, controller advice, security filter chain, and actuator metrics.

## Control flow

`POST -> validate -> idempotency check -> persist ACCEPTED -> resolve source policy -> ROUTED -> commit -> urgent async PROCESSING/DELIVERED`.

Non-urgent work follows `ROUTED -> PROCESSING -> QUEUED -> DELIVERED`; failures re-enter through durable retry scheduling.

## Concurrency decision

Register urgent processing with transaction synchronization after commit. The worker must reload committed state and recipients before mutation to avoid detached entities and optimistic-lock races.

## Migration

For brownfield adoption, retain the public request/status contract, move global channel policy into a source-system profile, preserve the publisher interface, and add tests before changing the implementation.

## Risks

Real Kafka/RabbitMQ integration, archive retention, distributed rate-limit state, and production identity remain deployment-specific extensions.
