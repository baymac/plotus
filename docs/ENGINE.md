# The planning engine

The planning engine is intended to help turn a site's constraints and a household's requirements into a residential floor-plan concept.

It considers plot geometry, setbacks, floors, room requirements and how spaces connect. Existing checks examine room fit, circulation and furniture placement. A designer still needs to review the result; a concept is not a construction document or structural approval.

## Availability

The Floor plan stage saves setbacks, building floors and homes, and each home’s room programme. A connected engine calculates the buildable envelope and runs bounded generation jobs. The app displays drawings only when the existing engine accepts the result. No-fit and unavailable states preserve the saved brief. Supported combinations are checked before generation; room sizing is automatic in this milestone.

Generation is account-owned and runs behind the project API. Reopening Floor plan recovers the latest job. A request ID prevents duplicate jobs if the start response is lost, and a project revision prevents generating against a stale boundary. An interrupted service job is marked failed rather than silently repeated.

## Existing technology

The engine and its CAD assets predate the hackathon and remain private. The public app references them through an optional private submodule and can be built without access to it. The hosted connection keeps the engine and service credential behind the app’s API. Public builds without it can still save requirements; preview and generation report that the service is unavailable.

The engine's Python service wrapper was created during the event. See [sources and credits](PROVENANCE.md) for the distinction between existing technology and new work.
