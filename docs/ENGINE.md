# The planning engine

The planning engine is intended to help turn a site's constraints and a household's requirements into a residential floor-plan concept.

It considers plot geometry, setbacks, floors, room requirements and how spaces connect. Existing checks examine room fit, circulation and furniture placement. A designer still needs to review the result; a concept is not a construction document or structural approval.

## Web concept model

New accepted generations also export an optional versioned `scene3d` for each drawing. This portable model contains metre-based footprint and room polygons, walls, openings, furniture placement and stairs from that same accepted floor. It omits source CAD, internal paths, solver state and asset fingerprints. The app stores it with the owner-scoped generation, so reopening or switching between 2D and 3D does not run the planning engine again. Missing vertical dimensions use explicit conceptual defaults; window sill and lintel geometry is reconstructed in the viewer.

The browser lazily loads an isolated React/Three.js viewer inside the Compose floor-plan screen. Kotlin retains navigation, authentication, project state and API access; the viewport receives only its scene and appearance. Earlier saved drawings without geometry remain 2D. The initial scope includes neutral conceptual furniture, cutaway/full-height walls, camera presets and per-floor viewing. Walkthrough, finish editing and AI rendering are deferred.

The mapping uses accepted geometry directly, without tracing a floor-plan image. Millimetres become metres, and the source is projected into the same planar orientation as the displayed 2D drawing. The viewer maps planar `(x, y)` to world `(x, height, y)`.

| Accepted 2D element | Concept model |
| --- | --- |
| Footprint, including holes | Thin floor slab preserving the outline and voids |
| Room polygons | Thin floor surfaces with selectable room names |
| Wall polygons with opening gaps | Extruded walls, with cutaway or full-height display |
| Doors and windows | Existing gaps plus sill and lintel bands; simple window frames |
| Furniture bounds and rotation | Neutral furniture primitives at the accepted placement; quarter-turn dimensions are converted into local axes |
| Shaped casework | Extrusion of its actual polygon, preserving L-shaped counters |
| Native stair dimensions and outline | Two rising flights and landings clipped to the accepted stairwell; older scenes retain schematic tread marks |

For deployment, update the project API before enabling scene export in the private engine, then deploy the web bundle. The optional field lets the new API read older drawings; an older API does not understand the new engine field. The private service changes must be committed in its own repository before updating this repository's submodule pointer.

## Availability

The Floor plan stage saves setbacks, building floors and a single residence’s room programme. A connected engine calculates the buildable envelope and runs bounded generation jobs. The app displays drawings only when the existing engine accepts the result. No-fit and unavailable states preserve the saved brief. Supported combinations are checked before generation; room sizing is automatic in this milestone.

Generation is account-owned and runs behind the project API. Floor plan opens its setup canvas; History lists earlier attempts by local date and time. Opening an entry shows its saved drawing on a read-only canvas with pan, zoom and fit, in the app’s current appearance. A running job continues when the screen is closed. A request ID prevents duplicate jobs if the start response is lost, and a project revision prevents generating against a stale boundary. An interrupted service job is marked failed rather than silently repeated.

The generated-plan view keeps room geometry and furniture, with a separate compass and a bottom summary of plot area, perimeter, built-up area and carpet area. Development review comments, sheet headings and outer overall dimensions are removed. The road is labelled “Road (primary)”. These presentation changes preserve the original saved generation and use its measurements rather than the project's current edits.

Export offers PNG and PDF for saved plans, and genuine DWG for new generations that retain vector artwork. Older image-only history entries need a new generation for DWG. Downloads use the normal verified-account project API; the browser downloads the file and iOS opens the system share sheet. The private hosted service uses [GNU LibreDWG](https://www.gnu.org/software/libredwg/) to write millimetre CAD linework and text. No private engine source is sent to the client.

## Existing technology

The engine and its CAD assets predate the hackathon and remain private. The public app references them through an optional private submodule and can be built without access to it. The hosted connection keeps the engine and service credential behind the app’s API. Public builds without it can still save requirements; preview and generation report that the service is unavailable.

The engine's Python service wrapper was created during the event. See [sources and credits](PROVENANCE.md) for the distinction between existing technology and new work.

The 28 October integration correction emits the actual presence flag for optional rooms, matching the engine's existing validation contract. The transport retains the native default of three architectural attempts, with one site attempt and a 240-second worker ceiling. Backend timeouts include that ceiling and preflight overhead. A bounded native repair can move an automatically inferred internal door to the other end of its shared wall when the fully open entrance blocks access. It preserves the entrance and all room geometry, widths and furniture, reruns every native check, and rejects moves that introduce failures. Explicit client openings stay fixed.

Buildable envelopes are stored by verified owner, project, plot and effective setback inputs. They are loaded with the project and remain available without another engine call. Floor count, room requirements and display units are excluded from that key. The area calculation uses a fixed geometry-only programme so building configuration cannot change the footprint. Derived-area writes do not change the project’s edit revision.
