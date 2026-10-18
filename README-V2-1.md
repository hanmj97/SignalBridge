# SignalBridge V2-1 — Opportunity Foundation

Start with [`docs/V2-1-APPLY.md`](docs/V2-1-APPLY.md).

This additive patch builds the data layer for the new youth policies/benefits/jobs platform while preserving the original GitHub release collector, history, auth, and Discord notifications.

**No actual youth policy/job data is bundled.** All three fixtures are synthetic and saved only with explicit `--write-demo`, marked `is_demo=true`, in the isolated `signalbridge_v2_lab` database only. Youth Center API integration will be mapped and verified only after the official key is approved.
