# Generic Agentic Orchestration

The `.github` directory is the complete, generic agentic implementation for converting natural-language requirements into reviewed software changes. It is not tied to the notification-service domain.

## Entry points

- [Agentic Orchestrator](agents/orchestrator.agent.md)
- [Orchestration prompt](prompts/orchestration.prompt.md)
- [Executable coordinator](orchestrator.sh)
- [Model preferences](model-preferences.md)

## Specialist agents

The coordinator delegates requirements, architecture, development, unit-test generation, validation, documentation, and production-readiness work. Each role has a discoverable agent definition under `agents/` and a reusable skill under `skills/`.

## Model policy

[model-preferences.md](model-preferences.md) defines provider-neutral model capabilities and role preferences. It does not force a vendor or model name. The host runtime maps capabilities such as reasoning, coding, context size, tool use, speed, and determinism to available models.

## State and evidence

Each run stores state, prompts, outputs, and bounded handoffs under `.agent-work/<run-id>/`. Handoffs classify claims as `PLANNED`, `MOCK_VERIFIED`, `LIVE_VERIFIED`, or `GAP`. Durable project evidence can be promoted separately by a documentation or validation agent.

## Scenarios

The same workflow supports:

- greenfield implementation from a new requirement
- brownfield enhancement with repository and compatibility analysis
- ambiguous requirements with explicit decisions before implementation

Project-specific behavior belongs in a run profile and handoff, never in the generic orchestrator contract.
