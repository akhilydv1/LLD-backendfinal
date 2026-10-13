# Assignment Traceability

This mapping describes how the current implementation addresses the known DesignLoop prototype requirements. Confirm any institution-specific submission checklist against the supplied official brief before handing in.

| Requirement/theme | Implementation evidence | Status / boundary |
|---|---|---|
| Multiple LLD exercises | `ProblemSeeder` supplies six problem definitions | Included |
| Problem requirements and a change scenario | `ProblemDocument` and `/api/v1/problems/{id}` | Included |
| Practice loop: choose, draft, submit, review, retry | `ProblemController`, `AttemptController`, `AttemptService`, `EvaluationWorker` | Included; verify with live end-to-end demo |
| Text submission | Structured draft sections | Included |
| Document submission | Multipart upload + Apache Tika extraction | Included for listed formats |
| Diagram submission | Image/PDF-page visual input and textual SVG/XML evidence | Included; unsupported visual rendering cases are reported rather than guessed |
| Combined submission | Text sections plus one or more document/diagram attachments | Included |
| Persistent drafts/history/submissions | MongoDB records | Included; file bytes additionally require upload directory persistence |
| Rubric feedback | Eight weighted criteria, per-criterion score/evidence/concern/suggestion | Included when Gemini is configured and returns valid JSON |
| Honest deterministic fallback | `RULES` / `READINESS_ONLY`, null score | Included when no Gemini key is configured |
| Async status/failure/retry | Attempt status, timeline, max run count, retry endpoint | Included; validate provider/quota failure manually |
| Idempotent submit and revision checking | `Idempotency-Key`, `expectedRevision`, Mongo version field | Included for the local single-service setup |
| Automated tests | Unit tests for rubric mapping, no-key fallback, revision conflict and required sections | Included; run `mvn test` locally |
| Documentation | README, design note, research note, testing guide, AI usage disclosure, API contract | Included |
| Public multi-learner security | Real authentication/authorization and secured file download | Not included; this is a local demo prototype and must not be publicly exposed as-is |
| Code execution / compilation | None | Intentionally out of scope; code is treated as evidence only |
