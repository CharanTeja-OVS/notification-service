---
name: state-and-handoffs
description: Maintains evidence-based Markdown state and bounded context between agents.
---

The coordinator owns `.agent-work/<run-id>/state.md`. Each agent writes a handoff containing mission, requirements, decisions, files, risks, validation commands/results, and next-agent instructions. Missing, stale, or unsupported evidence blocks advancement. Durable evidence may be promoted to `docs/agent-orchestration/evidence/`.

Handoffs must distinguish `PLANNED`, `MOCK_VERIFIED`, `LIVE_VERIFIED`, and `GAP`. Never promote a mock result to live evidence without a source, test, configuration, or runtime reference.

When a model is used, state records should include the role, permitted provider/model identifier, timestamp, tool availability, and whether the output was accepted, modified, rejected, or escalated. Use `undisclosed-by-policy` when details cannot be recorded.
