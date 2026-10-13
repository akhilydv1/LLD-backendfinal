# DesignLoop Backend

DesignLoop is a Low-Level Design (LLD) practice platform. Learners choose a problem, submit design evidence as structured text, a document, a diagram, or a combination, and receive rubric-based feedback. This repository contains the Spring Boot API and its supporting documentation.

## Key features

- Six seeded LLD problems: Parking Lot, Elevator, Vending Machine, Expense Sharing, Library Management, and Food Delivery.
- One API contract for the React + Vite frontend: problem catalogue, attempt lifecycle, optimistic draft revision, file uploads, submission, status, feedback, history, and retry.
- Four learner input modes: `TEXT`, `DOCUMENT`, `DIAGRAM`, and `COMBINED`.
- Document text extraction using Apache Tika for PDF, Word, OpenDocument, RTF, Markdown, and text files.
- Diagram evidence support for raster images and PDF pages sent to Gemini as visual inputs; SVG/Draw.io/XML is retained as textual source evidence.
- Asynchronous evaluation: the server saves and freezes a submission before asking Gemini to evaluate it.
- Rubric-based feedback with criterion scores from 0–4, evidence quotes, concerns, actionable suggestions, and a server-computed overall score from 0–100.
- Honest no-key mode: if Gemini is not configured, only readiness feedback is returned, with `overallScore: null`. The service never presents basic completeness checks as AI design evaluation.
- MongoDB persistence for problem definitions, drafts, submissions, attempt history, attachments metadata and feedback. File bytes are stored on local disk in development rather than in MongoDB documents.
- OpenAPI/Swagger docs, structured API errors, CORS settings, retry limits, file type/size validation, tests, and design/research/AI-usage notes.

## Technology stack

- Java 17
- Spring Boot 3.5.x
- Spring Web / Spring MVC
- Spring Data MongoDB
- Jakarta Bean Validation
- Apache Tika
- Apache PDFBox
- Google Gemini REST API (`generateContent`)
- Springdoc OpenAPI / Swagger UI
- JUnit 5 and Mockito

## Quick start (recommended: Docker Compose)

Requirements: Docker Desktop with Docker Compose.

1. Copy `.env.example` to `.env` in this folder.
2. Open `.env` and set your `GEMINI_API_KEY`. Leave it blank only when you intentionally want readiness-only fallback. Create or view a key at <https://aistudio.google.com/apikey>.
3. Run from the `designloop-backend` directory:

   ```bash
   docker compose up --build
   ```

4. Wait until MongoDB reports healthy and the backend log reports `Started DesignLoopApplication`. Then open:
   - API health: <http://localhost:8080/api/v1/health>
   - API metadata: <http://localhost:8080/api/v1/meta>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>

The compose stack stores MongoDB data in a Docker volume and uploaded files in `./data/uploads`. Stop it with `Ctrl+C`; run `docker compose down` to stop/remove the containers. `docker compose down -v` also deletes the database volume and therefore removes stored attempts.

## Run from IntelliJ IDEA / Maven

Requirements: JDK 17 and Maven 3.6.3 or later.

1. Start MongoDB locally (or use MongoDB Atlas).
2. Set these environment variables in the Run Configuration:
   - `MONGODB_URI=mongodb://localhost:27017/designloop`
   - `GEMINI_API_KEY=your-key` (optional for readiness-only mode)
   - `GEMINI_MODEL=gemini-3.8-flash` (optional; configurable because model availability can change)
3. Open `pom.xml` as a Maven project and run `com.designloop.DesignLoopApplication`, or run:

   ```bash
   mvn spring-boot:run
   ```

For a MongoDB Atlas URI, set `MONGODB_URI` to the URI supplied by Atlas; do not commit the URI or credentials.

## Connect the frontend

Use the matching frontend folder and its `.env.example`:

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_LEARNER_ID=d847c7fe-2d42-49d4-8e4d-dde0acbbfd6a
```

Run the frontend with `npm install` then `npm run dev`. The backend allows Vite's usual local origins on ports 5173 and 5174. If you use another port, add it to `CORS_ALLOWED_ORIGINS` and restart the backend.

**API contract:** See [BACKEND_API_CONTRACT.md](BACKEND_API_CONTRACT.md). Frontend and backend endpoint names and JSON formats are designed to match that document.

## API overview

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/problems` | List seeded problems |
| `GET` | `/api/v1/problems/{id}` | Problem details and rubric |
| `POST` | `/api/v1/attempts` | Create a draft |
| `PUT` | `/api/v1/attempts/{id}/draft` | Save a draft revision |
| `POST` | `/api/v1/attempts/{id}/attachments` | Upload a document or diagram |
| `POST` | `/api/v1/attempts/{id}/submit` | Freeze evidence and queue evaluation (202) |
| `GET` | `/api/v1/attempts` | Current demo learner's history |
| `GET` | `/api/v1/attempts/{id}` | Status, draft, submission, feedback and timeline |
| `GET` | `/api/v1/attempts/{id}/feedback` | Completed feedback |
| `POST` | `/api/v1/attempts/{id}/retry-evaluation` | Retry an eligible failed run |
| `GET` | `/api/v1/meta` | Supported modes, limits and current evaluator |
| `GET` | `/api/v1/health` | Basic application health |

All learner-attempt endpoints require `X-Learner-Id`. Submit also requires a unique `Idempotency-Key`. See Swagger for the full request schemas and response codes.

## Evaluation behaviour

When `GEMINI_API_KEY` is set, submissions are sent to the configured Gemini model with the selected problem requirements, change scenario, rubric, learner text/document extraction, and supported diagram image evidence. The model provides per-criterion assessment and evidence; the server validates criterion scores and calculates the weighted total.

When the key is blank, the application uses a transparent `RULES`/`READINESS_ONLY` mode. It can report that sections and attachments are present, but it returns no design score. If the configured Gemini call fails or its JSON fails rubric validation, the attempt becomes `FAILED` and may be retried within the configured run limit. The submitted evidence remains saved.

Do not treat AI output as infallible. The feedback is a learning aid, not an authoritative proof of correctness.

## File handling and limits

- Default max file size: 20 MiB per file, with a separate configurable count limit per attempt.
- Documents: PDF, DOC, DOCX, TXT, MD, RTF, ODT.
- Diagrams: PNG, JPG/JPEG, WEBP, SVG, PDF, Draw.io, XML.
- Binary file contents are kept under `UPLOADS_DIR`; MongoDB stores metadata and extracted text. Back up both the database and upload directory for a complete local restore.
- This is a local prototype. Replace local-disk storage with private object storage and malware scanning before multi-instance or public use.

## Tests

Run:

```bash
mvn test
```

The included unit tests cover readiness-only honesty, weighted rubric score calculation, invalid model score rejection, evidence-quote validation, attachment type/content handling, stale draft revisions, and rejection of incomplete text submissions. These tests do not call the live Gemini API or require MongoDB.

## Security and prototype boundary

`X-Learner-Id` is a demo ownership header, not authentication. A user can spoof it. Do not expose this app publicly or treat learner isolation as secure until authentication/authorization is implemented. Keep Gemini credentials on the server, configure CORS to exact origins, do not commit `.env`, and restrict upload storage and access. Production hardening should add real authentication, rate limiting, provider usage controls, virus scanning, private object storage, database backups, and privacy/retention settings.

## Documentation

- [Design note](docs/DESIGN.md)
- [Research note](docs/RESEARCH_NOTE.md)
- [Assignment traceability](docs/ASSIGNMENT_TRACEABILITY.md)
- [Testing guide](docs/TESTING.md)
- [AI usage disclosure](AI_USAGE.md)
- [API contract](BACKEND_API_CONTRACT.md)
