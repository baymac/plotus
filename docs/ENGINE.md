# The planning engine

The planning engine is intended to help turn a site's constraints and a household's requirements into a residential floor-plan concept.

It considers plot geometry, setbacks, floors, room requirements and how spaces connect. Existing checks examine room fit, circulation and furniture placement. A designer still needs to review the result; a concept is not a construction document or structural approval.

## Availability

The Floor plan stage saves setbacks, building floors and a single residence’s room programme. A connected engine calculates the buildable envelope and runs bounded generation jobs. The app displays drawings only when the existing engine accepts the result. No-fit and unavailable states preserve the saved brief. Supported combinations are checked before generation; room sizing is automatic in this milestone.

Generation is account-owned and runs behind the project API. Floor plan opens its setup canvas; History lists earlier attempts by local date and time. Opening an entry shows its saved drawing on a read-only canvas with pan, zoom and fit, in the app’s current appearance. A running job continues when the screen is closed. A request ID prevents duplicate jobs if the start response is lost, and a project revision prevents generating against a stale boundary. An interrupted service job is marked failed rather than silently repeated.

## Existing technology

The engine and its CAD assets predate the hackathon and remain private. The public app references them through an optional private submodule and can be built without access to it. The hosted connection keeps the engine and service credential behind the app’s API. Public builds without it can still save requirements; preview and generation report that the service is unavailable.

The engine's Python service wrapper was created during the event. See [sources and credits](PROVENANCE.md) for the distinction between existing technology and new work.

The 28 October integration correction emits the actual presence flag for optional rooms, matching the engine's existing validation contract. The transport retains the native default of three architectural attempts, with one site attempt and a 240-second worker ceiling. Backend timeouts include that ceiling and preflight overhead. A bounded native repair can move an automatically inferred internal door to the other end of its shared wall when the fully open entrance blocks access. It preserves the entrance and all room geometry, widths and furniture, reruns every native check, and rejects moves that introduce failures. Explicit client openings stay fixed.

Buildable envelopes are stored by verified owner, project, plot and effective setback inputs. They are loaded with the project and remain available without another engine call. Floor count, room requirements and display units are excluded from that key. The area calculation uses a fixed geometry-only programme so building configuration cannot change the footprint. Derived-area writes do not change the project’s edit revision.
