# Generic LLM Model Preferences

This policy is provider-neutral. It describes the capabilities and behavior expected from a model selected for each orchestration role. A host environment may map these preferences to different model names, versions, or endpoints.

## Selection principles

- Prefer the strongest reasoning-capable model available for requirements, architecture, conflict resolution, security, and final release decisions.
- Prefer a reliable code-capable model for implementation and test generation, with repository access and structured tool use.
- Prefer a fast model for repetitive documentation, evidence normalization, and low-risk summarization after the source evidence is known.
- Use the same model family or compatible context format across handoffs when preserving reasoning continuity matters.
- Never select a model based only on speed when the task involves data loss, security, concurrency, migrations, or public API changes.
- Model selection is an execution preference, not evidence that a task was completed.

## Role preferences

| Role | Preferred capability | Autonomy | Required context | Escalate when |
|---|---|---|---|---|
| Orchestrator | Highest reasoning, planning, and tool coordination | Plan and delegate; do not silently implement | Requirement, state, all applicable skills, prior handoffs | Scenario, ownership, or acceptance criteria remain unclear |
| Requirements | Strong reasoning and ambiguity analysis | Analyze and propose decisions | Natural-language request, project evidence, scenario | A requirement cannot be made testable |
| Architecture | Strong systems reasoning and trade-off analysis | Design and review; no unapproved code changes | Requirements, repository structure, constraints | Requirements do not map to components or data |
| Developer | Strong code generation, repository navigation, and tool use | Implement approved changes in small slices | Requirements, architecture, current code, tests | Existing behavior conflicts with the design |
| Unit-test generation | Strong testing and edge-case reasoning | Add tests and fixtures | Requirements, architecture, changed code | Observable behavior cannot be asserted deterministically |
| Tester/validation | Independent reasoning and reproducible execution | Run commands and report facts | All handoffs, source, configuration, build artifacts | Evidence is missing, flaky, or contradicts code |
| Documentation | Accurate synthesis with source traceability | Update docs only from evidence | Requirements, decisions, implementation, validation | A documentation claim lacks evidence |
| Production readiness | Highest risk, security, and release reasoning | Gate and return work to owners | Full state, diff, tests, runtime evidence, risks | Security, data integrity, rollback, or requirement gaps remain |

## Generation and context controls

- Keep temperature or equivalent randomness low for code, tests, migrations, state, and evidence; use deterministic generation where supported.
- Use structured Markdown output with stable headings and identifiers such as `REQ-NNN`, `DEC-NNN`, and `GAP-NNN`.
- Keep each handoff bounded to the next agent's mission; reference artifacts instead of duplicating large content.
- Preserve exact commands, exit codes, test counts, coverage, timestamps, and file paths.
- Treat model output as a proposal until verified against the repository or runtime.
- Do not place credentials, tokens, private keys, or unnecessary personal data in prompts, state, handoffs, or evidence.

## Model execution record

Every non-mock run should record the following in `state.md`:

- provider or runtime identifier, if policy permits recording it
- model identifier and version, if policy permits recording it
- role that used the model
- execution timestamp
- whether tools were available
- whether output was accepted, modified, rejected, or escalated
- validation evidence for accepted output

If the provider or model is intentionally undisclosed, record `undisclosed-by-policy`; never invent a model name.
