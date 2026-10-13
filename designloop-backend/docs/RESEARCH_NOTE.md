# Research Note — Feedback for Multimodal LLD Practice

## Problem being addressed

Learners often explain a design in different ways: structured notes, a prepared document, a class/sequence diagram, or a mixture. Requiring one submission shape makes the practice tool less useful, while treating the presence of text as proof of design quality provides misleading feedback. DesignLoop keeps input format flexible and evaluates the evidence against the selected problem's requirements and rubric.

## Research questions

1. How can the application accept text, documents and diagrams using one coherent attempt lifecycle?
2. How can feedback explain why a design is weak or strong without requiring a single reference answer?
3. How should the product behave when the model is unavailable or returns invalid output?

## Findings and design decisions

### One attempt lifecycle, multiple evidence types

A common attempt/submission model avoids separate workflows for text and file-based learners. The format is explicit (`TEXT`, `DOCUMENT`, `DIAGRAM`, `COMBINED`), while evidence is normalized for evaluation: text fields remain structured, document files are converted into bounded extracted text, and supported diagram image pages can be sent as visual evidence. This makes history, retries, timestamps and ownership behave consistently across modes.

### Separate readiness from quality assessment

Presence checks can establish only that a learner supplied content and that a request fits the configured limits. They cannot establish cohesion, class responsibility, state correctness, or extensibility. Consequently the service uses a clearly labelled readiness-only response when `GEMINI_API_KEY` is absent. It does not fabricate numeric scores in this mode. A successful Gemini review must provide criterion-level scores and evidence, and the server computes the weighted score only after validating the response.

### Rubric-based, evidence-oriented feedback

A shared 0–4 scale is used for eight criteria, weighted to a total of 100. Weights make the overall score explainable and let the UI present a consistent review layout. Model instructions ask for short evidence quotes, one specific concern and one actionable suggestion per criterion. The evaluator is told to accept multiple valid designs and not award credit for pattern names alone. The server validates every criterion ID and score before presenting results.

### Preserve the work when external evaluation fails

The submission snapshot is persisted before the asynchronous model call begins. The model request runs outside the submit HTTP request. If a provider error, quota issue, network failure or malformed response occurs, the status becomes `FAILED`, the original submission remains available, and retry is allowed within a configured run limit. This is safer than losing a design or returning a made-up score.

### Keep upload bytes out of the database record

Binary file bodies can be much larger than attempt metadata. For the local prototype, files are stored in a dedicated upload directory and MongoDB stores metadata, checksum and bounded extracted text. A production version should use private object storage with access controls, retention rules and malware scanning.

## Limitations

- Model feedback can be mistaken or incomplete; feedback is educational guidance, not proof of correctness.
- Document extraction may lose layout, and XML-based diagram formats may be less visually legible than raster images.
- The prototype accepts a demo learner identity header rather than full authentication.
- Local file storage is suitable for local evaluation, not a scaled multi-instance service.
- Submitted code is treated as evidence only; it is not compiled or executed.

## References

- Google AI for Developers, Gemini API: Generate content — <https://ai.google.dev/api/generate-content>
- Spring Boot system requirements — <https://docs.spring.io/spring-boot/3.5/system-requirements.html>
- Spring Guides, Accessing Data with MongoDB — <https://spring.io/guides/gs/accessing-data-mongodb/>
- Apache Tika documentation — <https://tika.apache.org/>
- Apache PDFBox documentation — <https://pdfbox.apache.org/>

## Conclusion

The practical design choice is to keep submission, evidence storage, and evaluation separate. That separation enables multimodal input without duplicating the entire domain model, keeps asynchronous model failures recoverable, and makes the product's feedback claims honest.
