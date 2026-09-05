# Scenarios

The scenario catalog is available at `GET /api/scenarios`.

## Brownfield Analytics

`brownfield-analytics` runs the normal deterministic workflow and generates a
URL-shortener analytics enhancement proposal, generated tests, validation evidence, risk
review, release-readiness output, and an engineering outcome inside an isolated workspace.
The generated service evidence records `BROWNFIELD_ANALYTICS_ENHANCEMENT`.

## Greenfield URL Shortener

`greenfield-url-shortener` uses the same agent contracts and patch pipeline to generate a
runnable URL-shortener vertical slice in an isolated workspace. The generated service
evidence records `GREENFIELD_VERTICAL_SLICE`.

## Repair Demonstration

`repair-demonstration` deliberately generates a compiler failure, stores the failed Maven
validation evidence, invokes the repair agent, applies a corrected file-operation proposal,
and revalidates successfully.

## Ambiguous Requirement

`ambiguous-requirement` demonstrates general ambiguity handling. The requirement
`Make links better.` produces clarification questions and pauses in
`AWAITING_CLARIFICATION` without mutating source.
