# DesignLoop API Contract

Base URL: `http://localhost:8080` in local development. JSON uses `application/json`; structured errors use `application/problem+json`.

## Common headers

- `X-Learner-Id`: required for learner-specific endpoints. Local demo identity only; not authentication.
- `Idempotency-Key`: required for `POST /api/v1/attempts/{id}/submit`.
- Upload endpoint uses `multipart/form-data`, with fields `file`, `kind` (`DOCUMENT` or `DIAGRAM`), and optional `notes`.

## Problem endpoints

- `GET /api/v1/problems` returns a problem array.
- `GET /api/v1/problems/{problemId}` returns the problem, requirements, assumptions, out-of-scope notes, change scenario and rubric criteria.

Problem JSON fields: `id`, `title`, `difficulty`, `suggestedMinutes`, `description`, `category`, `skills`, `requirements` (objects with `id` and `description`), `assumptions`, `outOfScope`, `changeScenario`, `rubricVersion`, and `criteria`.

## Attempts

### Create
`POST /api/v1/attempts`

```json
{"problemId":"parking-lot","submissionFormat":"TEXT"}
```

`submissionFormat`: `TEXT`, `DOCUMENT`, `DIAGRAM`, or `COMBINED` (defaults to `TEXT`). Response includes `id`, `attemptId`, `problemId`, `status`, `revision`, `draft`, and associated problem metadata.

### Save draft
`PUT /api/v1/attempts/{id}/draft`

```json
{
  "expectedRevision": 0,
  "artifact": {
    "format": "STRUCTURED_TEXT",
    "sections": {
      "requirements": "...",
      "responsibilities": "...",
      "relationships": "...",
      "behavior": "...",
      "tradeoffs": "...",
      "tests": "...",
      "code": "optional"
    },
    "notes": "optional context"
  }
}
```

Allowed artifact formats: `STRUCTURED_TEXT`, `DOCUMENT`, `DIAGRAM`, `COMBINED`. The frontend maps `TEXT` mode to `STRUCTURED_TEXT`. Partial drafts are allowed; required content is validated on submit. Draft saves use optimistic revision checks and return 409 on stale revision. The six required text sections have minimum lengths of 80 (`requirements`), 100 (`responsibilities`), 60 (`relationships`), 120 (`behavior`), 80 (`tradeoffs`), and 100 (`tests`) characters at submit time. Non-code sections are limited to 6,000 characters each, `code` is limited to 12,000, and all sections are limited to 36,000 characters total.

### Upload evidence
`POST /api/v1/attempts/{id}/attachments` (`multipart/form-data`)

- `file`: one file
- `kind`: `DOCUMENT` or `DIAGRAM`
- `notes`: optional

Documents: PDF, DOC, DOCX, TXT, MD, RTF, ODT. Diagrams: PNG, JPG/JPEG, WEBP, SVG, PDF, Draw.io, XML. Default max file size is 20 MiB. Upload only while status is `DRAFT`. `DELETE /api/v1/attempts/{id}/attachments/{attachmentId}` removes an uploaded attachment from an editable draft.

### Submit
`POST /api/v1/attempts/{id}/submit` with `Idempotency-Key`.

Returns `202 Accepted` and a small response including `attemptId`, `status`, `revision`, `evaluationRun`, `replayed`, `statusUrl`, and `feedbackUrl`. Evidence is frozen and saved before background evaluation starts. Repeated submission does not create a new evaluation run.

### Read and retry

- `GET /api/v1/attempts/{id}` returns status, draft, frozen submission, safe attachment metadata, evaluation counters, feedback (if present), failure and timeline.
- `GET /api/v1/attempts` returns the current demo learner's attempts.
- `GET /api/v1/attempts/{id}/feedback` returns feedback when complete; returns 409 while pending or after failure.
- `POST /api/v1/attempts/{id}/retry-evaluation` retries a failed evaluation while runs remain. The same frozen evidence is reused.

Status values: `DRAFT`, `SUBMITTED`, `EVALUATING`, `COMPLETED`, `FAILED`.

## Feedback schema

Successful Gemini evaluation includes `provider: GEMINI`, `model`, `assessmentType: RUBRIC_BASED_DESIGN_REVIEW`, `overallScore` (0–100, calculated on the server), per-criterion `score` (0–4), evidence quotes, concerns, suggestions and next steps. If Gemini is not configured, feedback contains `provider: RULES`, `assessmentType: READINESS_ONLY` and `overallScore: null`. A failed Gemini call is not silently turned into a fake AI score.

## Other endpoints

- `GET /api/v1/meta`: client contract, allowed formats, size policies and active evaluator.
- `GET /api/v1/health`: lightweight application health.
- `GET /swagger-ui.html`: Swagger UI.

## Error shape

```json
{
  "type": "urn:designloop:problem:invalid-artifact",
  "title": "Bad Request",
  "status": 400,
  "detail": "Explain what needs to be corrected.",
  "instance": "/api/v1/attempts/example/draft",
  "code": "INVALID_ARTIFACT",
  "requestId": "uuid",
  "fieldErrors": [{"field":"artifact.sections","message":"A required section is missing."}]
}
```
