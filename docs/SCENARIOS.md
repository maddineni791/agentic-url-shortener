# Scenarios

The scenario catalog is available at `GET /api/scenarios`. Every scenario runs the same
two-segment lifecycle: the design segment pauses in `AWAITING_CHANGE_APPROVAL` after
publishing `engineering-plan.json`, and the build segment (task graph, patch, validation,
repair, docs, risk review) runs only after the change gate receives the exact plan hash,
finishing in `AWAITING_RELEASE_APPROVAL`.

## Brownfield Analytics

`brownfield-analytics` inspects the existing shortener conventions and generates a
URL-shortener analytics enhancement proposal, generated tests, validation evidence, risk
review, release-readiness output, and an engineering outcome inside an isolated workspace.
The generated service evidence records `BROWNFIELD_ANALYTICS_ENHANCEMENT`.

## Greenfield URL Shortener

`greenfield-url-shortener` uses the same agent contracts and patch pipeline to generate a
runnable URL-shortener vertical slice (service, domain record, REST controller, request
DTO, behavior tests) in an isolated workspace. The generated service evidence records
`GREENFIELD_VERTICAL_SLICE`.

## Repair Demonstration

`repair-demonstration` deliberately generates a compiler failure, stores the failed Maven
validation evidence, invokes the repair agent, applies a corrected file-operation proposal,
and revalidates successfully. If the bounded repair budget were exhausted, the workspace is
rolled back to its verified baseline and `rollback-evidence.json` is written.

## Ambiguous Requirement

`ambiguous-requirement` demonstrates general ambiguity handling. The requirement
`Make links better.` produces clarification questions and pauses in
`AWAITING_CLARIFICATION` without mutating source. The ambiguity agent reads missing or
conflicting dimensions from the normalized requirement artifact, so arbitrary unclear
requirements can pause even without a special scenario phrase. Submitting a clarification
(`POST /api/workflows/{id}/clarifications`) opens revision 2 with the enriched requirement,
preserves the original requirement and answer lineage, and resumes the lifecycle from
requirement analysis.
