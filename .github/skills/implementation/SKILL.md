---
name: implementation
description: Implements approved architecture incrementally with evidence and regression control.
---

Make minimal coherent changes, preserve contracts, protect data integrity, keep external effects behind adapters, and validate each slice. Do not hide failures or invent silent fallbacks. Record changed files, decisions, risks, and exact commands in the state handoff.

Treat mock behavior as a hypothesis, not evidence. Verify every planned side effect is invoked on every execution path. For queued work, prove the sender, retry policy, rate limiter, persistence update, and audit event are called; otherwise record a `GAP-NNN` for the next implementation slice.
