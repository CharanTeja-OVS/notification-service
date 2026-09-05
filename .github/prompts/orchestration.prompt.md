# Generic Agentic Orchestration Prompt

Natural-language requirement:

> <requirement>

Scenario: `<greenfield|brownfield|ambiguous>`
Target project: `<project-path>`
Run ID: `<run-id>`

Act as the generic Agentic Orchestrator. Read `.github/agents/orchestrator.agent.md`, the applicable `.github/skills/*/SKILL.md`, and `.agent-work/<run-id>/state.md`.

Execute requirements, architecture, development, unit-test generation, tester/validation, documentation, and production-readiness stages in order. Each stage must write a Markdown handoff under `.agent-work/<run-id>/context/`, update state, and include exact evidence. Do not claim a stage passed without an artifact or validation result.

The target project may be a new system or an existing system. Preserve project-specific behavior in a profile or handoff; do not turn one implementation into a generic orchestration rule.
