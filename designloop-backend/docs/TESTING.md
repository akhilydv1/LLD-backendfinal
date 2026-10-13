# Testing Guide

## Automated tests

Run from this backend directory:

```bash
mvn test
```

The current unit tests check:

- no-key mode returns `READINESS_ONLY` with null overall and criterion scores;
- a valid Gemini rubric response is mapped to the expected criteria and weighted score;
- out-of-range model scores are rejected;
- stale draft revisions return a conflict;
- an incomplete text attempt cannot be submitted;
- image uploads retain notes and server-side bytes, while unsupported extensions are rejected.

These unit tests are offline: they do not call Gemini and do not require MongoDB. They are not a substitute for endpoint-level integration and browser testing.

## Manual smoke test

1. Start MongoDB and the backend. Verify `GET /api/v1/health`, `GET /api/v1/meta` and `/swagger-ui.html`.
2. Set `X-Learner-Id` to a consistent local identity such as `d847c7fe-2d42-49d4-8e4d-dde0acbbfd6a`.
3. List problems, create a `TEXT` attempt, save all six required sections with `expectedRevision: 0`, submit with a unique `Idempotency-Key`, then poll `GET /api/v1/attempts/{id}` until it is `COMPLETED` or `FAILED`.
4. Fetch `GET /api/v1/attempts/{id}/feedback`. With a valid Gemini key, `provider` should be `GEMINI` and `assessmentType` should be `RUBRIC_BASED_DESIGN_REVIEW`. Without a key, a correct fallback is `RULES` / `READINESS_ONLY` and all scores remain null.
5. Create a `DOCUMENT` attempt, upload a small DOCX or PDF using multipart fields `file`, `kind=DOCUMENT`, and `notes`, then submit. Verify attachment metadata and extracted evidence survive refresh.
6. Create a `DIAGRAM` attempt and upload a small PNG/JPG or a PDF diagram. Verify submission and feedback include visual evidence when Gemini is configured.
7. Create a `COMBINED` attempt; save required text sections; upload a diagram or document; submit and review.
8. Test a bad extension, file above the size limit, stale `expectedRevision`, missing required text section, wrong learner identity, and duplicate submit using the same `Idempotency-Key`.
9. Temporarily use an invalid/unavailable model configuration or key in a local environment to observe `FAILED`, confirm the submitted evidence is still available, and test retry. Restore a valid key afterwards.
10. Connect the React frontend using `VITE_API_BASE_URL=http://localhost:8080` and verify the same path from the learner UI, not only Swagger.

## Evidence to include in a demo

Capture one successful attempt for each input mode, one completed Gemini feedback screen with scores/evidence, and one recovery case where evaluation fails but the submission remains saved. Do not present the no-key readiness fallback as AI assessment.
