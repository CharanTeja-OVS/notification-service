# Mock-versus-Live Drift Review

- **Review status**: COMPLETE
- **Reviewed**: 2026-09-04
- **Target**: notification-service reference profile
- **Fresh reconciliation run**: `reconciliation-mock` completed with 7 context handoffs and a state file.

## Evidence classification

| Item | Mock claim | Live evidence | Classification | Gap/owner |
|---|---|---|---|---|
| Source-system routing | Policy owns routing and enabled channels | `ChannelProperties`, `NotificationRoutingService`, `application.yml`, routing tests | LIVE_VERIFIED | None |
| Source/channel rate limits | Independent limiter per `(sourceSystem, channel)` | `ResilientNotificationSender`, `ResilientNotificationSenderTest` | LIVE_VERIFIED | None |
| Urgent post-commit dispatch | Urgent work starts after transaction commit | `NotificationService.dispatchUrgentAfterCommit`, focused submission tests | LIVE_VERIFIED | None |
| Urgent resilient sender | Sender rate limit/retry applies to urgent sends | `DeliveryProcessorService` invokes `ResilientNotificationSender` for urgent channels | LIVE_VERIFIED | None |
| Deferred resilient sender | Queued work sends through the same sender before delivery | `NotificationScheduler.processQueuedNotifications` finalizes status without invoking `DeliveryProcessorService` or sender | GAP-001 | Developer: implement queued outbound send and tests |
| Failure classification | Provider failures populate `FailureCategory` | `FailureCategory` exists, but `ResilientNotificationSender` throws without assigning a category to `DeliveryAttempt` | GAP-002 | Developer: classify transient, permanent, invalid recipient, rate-limit, timeout, and auth failures |
| Full audit history | All significant actions are audit-visible | Routing audit exists; accepted, attempt, success, retry, and failure audit events are not consistently written | GAP-003 | Developer: complete audit event persistence and tests |
| Detailed status response | Status retrieval includes recipient/channel delivery details and timestamps | `NotificationResponse` currently returns overall status, selected channels, and key timestamps only | GAP-004 | Developer: expose delivery details or document a separate endpoint |

## Conclusion

The mock workflow correctly captured the architecture direction, but it overstated implementation completeness for deferred sending, failure classification, audit completeness, and detailed delivery status. These are now explicit gaps and must not be silently marked complete by future runs.

The current implementation and the mock are aligned on the implemented behavior listed above, while `GAP-001` through `GAP-004` are intentionally not treated as aligned or complete.
