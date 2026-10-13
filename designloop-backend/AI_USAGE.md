# AI Usage Disclosure

## How AI is used in DesignLoop

DesignLoop uses the Google Gemini API on the server to provide rubric-based Low-Level Design feedback when `GEMINI_API_KEY` is configured.

The evaluation prompt includes:

- the selected problem's requirements, assumptions, out-of-scope items, and change scenario;
- the shared weighted evaluation rubric;
- structured learner text and optional supporting code;
- extracted text from uploaded documents;
- supported diagram image/page content and textual source evidence when available.

The model is asked to evaluate each criterion, include short evidence quotes, explain concrete concerns, and give actionable improvement suggestions. It is instructed to accept multiple valid designs and not to treat a named design pattern as proof of quality. The backend validates criterion identifiers and scores, computes the weighted overall score, and rejects malformed feedback.

## No-key and failure behaviour

When no API key is configured, the service returns `RULES` / `READINESS_ONLY` feedback. This mode can report basic section/attachment readiness but cannot assess design quality, so it returns no numeric scores. If a Gemini call fails or returns invalid rubric data, the attempt becomes `FAILED`; the submission is preserved and can be retried if the configured run limit allows it. The service does not silently fabricate an AI score.

## Data sent to the model

The selected problem, learner-provided design text, bounded document text, attachment context, and supported diagram image data are sent to the Gemini API for the purpose of generating feedback. Do not submit secrets, production credentials, personal data, or confidential company designs in this prototype. Read the provider's terms and configure data retention/consent before public or classroom deployment.

## Data not executed

Learner-provided code is reviewed as text only. The application does not compile or execute uploaded code. Local upload files remain on the server's configured upload directory and are not embedded as binary data in MongoDB records.

## Human review

AI feedback can be wrong or miss an issue. The feedback is educational guidance and should be checked against the problem requirements and the learner's own tests.

## Development assistance

Generative AI was used as a coding and documentation assistant while developing this prototype. The developer remains responsible for understanding the code, running tests, checking package versions, reviewing model-generated feedback, and disclosing any course-specific use of AI according to the institution's rules.
