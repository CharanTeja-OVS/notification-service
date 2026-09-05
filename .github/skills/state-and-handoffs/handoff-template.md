# Agent Handoff

- **Run ID**: <run-id>
- **From**: <agent>
- **To**: <next-agent>
- **Status**: READY | BLOCKED | REJECTED
- **Evidence classification**: PLANNED | MOCK_VERIFIED | LIVE_VERIFIED | GAP
- **Timestamp**: <timestamp>

## Mission

<what the next agent must accomplish>

## Requirements

- <REQ-ID>: <testable requirement>

## Accepted Decisions

- <DEC-ID>: <decision and rationale>

## Current Evidence

- <file, symbol, command, or artifact>

## Required Changes

- <specific implementation or validation task>

## Gaps and Risks

- <GAP-ID or risk, owner, mitigation, acceptance condition>

## Validation Contract

- Command: `<command>`
- Expected result: <result>
- Required evidence: <artifact or output>

## Agent Response Contract

The receiving agent must append files changed, requirements completed and remaining, decisions accepted or rejected, exact tests/checks, evidence classification, risks, and the recommended next agent.
