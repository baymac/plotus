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

## Architect review and optimization

Saved generated plans support Review plan, AI recommendations and Optimize. Balanced V1 calculates six deterministic category scores with weights 25/20/20/15/10/10 (space, circulation, function, daylight, privacy, access). Vastu remains a separate score in the saved true-north frame. Native layout checks are independent gates; structural, jurisdiction-specific approval, stair-section headroom and drainage are shown as unassessed. Missing historical evidence never becomes a guessed score.

The private service keeps immutable, programme-bound analysis evidence behind an opaque ID. The app API caches the measured report against the owned generation. The AI adapter uses the existing Meta provider configuration, with optional `PLOTUS_REVIEW_MODEL_API_KEY`, `PLOTUS_REVIEW_MODEL_ENDPOINT` and `PLOTUS_REVIEW_MODEL` overrides. It receives structured findings, not account identities or editable source. Its strict response selects up to three priorities, explains why each matters and names a concrete next step to test. Every critical finding must be retained. Recommendations reference existing finding IDs; the app maps each finding to its allowlisted solver goal. Geometry, scores and executable instructions are rejected. Provider failure leaves the measured review and solver available.

Optimize starts with one goal from the measured findings and shows its starting measurement. The app requests up to three attempts; the private transport accepts one to ten for existing integrations. Each selected goal must measurably improve, and a complete baseline's overall quality must not decline. An unrelated score gain cannot qualify. A useful dimension or wall change can qualify within the same room arrangement. Selected room geometry and its neighbours may be released from inferred prior guidance; explicit dimensions and native checks remain authoritative. The comparison names the improved goal and its measured before/after values. No improvement and no valid alternative are distinct outcomes; neither establishes impossibility.

An earlier same-brief arrangement can guide a new source solve when its measured target is better. It is fully checked again before becoming a revision. Original automatic-policy inputs are retained for historical analysis through an exact legacy payload-hash match; an old plan is not reinterpreted using newer default room requirements. Missing archived evidence disables local optimization instead of starting an unrelated cold generation.

The test ends when a checked candidate meets the selected goal. Three attempts is an upper limit; the app does not claim a globally optimal layout.

Optimization jobs persist by verified owner and project. History remains accessible during work; cancellation stops the matching private worker. Comparison pins the exact revision shown to the user. Accepting it checks that hash and creates a new immutable history entry with the original saved brief, preserving the base generation. Retried starts and accepts are idempotent. Review screens share the existing vector canvas, unit preferences, floor selection and room geometry for highlighting. Mobile uses full pages and desktop uses a side panel.

## Client PDF report

PDF export now includes the entire selected generation: saved plot orientation, boundary names/dimensions and roads, all generated floors, deterministic isometric cutaways from each saved portable scene, measured design/Vastu findings and cached or freshly requested AI explanations. It uses the generation's saved plot/brief, never the current editor's geometry. Project/client name and location come from the verified project; email/phone are not sent to the renderer. PNG and DWG stay per-floor exports.

The client report embeds Plotus's Plus Jakarta Sans fonts, uses measured text wrapping and a spacious landscape layout, and separates drawings, review scores and design checks. Its cover features the saved 3D geometry. Repeated explanatory subtexts and technical reference labels are removed; assessment results and grounded recommendations remain visible. Long project details paginate instead of clipping, and floor drawings remain vector artwork.

The report renderer uses vector PDF pages and local saved geometry; external SVG resources are rejected. Old plans without 3D or measured evidence say unavailable/not assessed. Web export preparation allows 90 seconds for PDF, PNG and DWG, with an 85-second gateway budget. Native PDF requests allow 90 seconds and the private report render request allows 60 seconds. Browser downloads use the authenticated bytes already prepared in the app: a normal Compose button synchronously invokes a Blob download anchor outside the Compose dialog. Object URLs are released after 60 seconds. A bounded AI request may fall back to the measured review if the provider is unavailable; missing AI text is never invented.

Auto uses the fixed room programme with Vastu enabled, default parking and staircase. Dressing, utility and a garden remain optional when geometry permits. Custom exposes the Vastu choice and editable room/parking entries without a separate Parking Yes/No toggle. Stored Custom drafts are retained when switching modes; ignored Auto extras cannot silently add a required garden. New engine programmes identify the Auto V2 contract explicitly.

The 4 November fallback-generation experiment has been removed. Generation again uses the original checked native selection path and its full worker budget. Historical drafts retain their stored labels, unresolved checks and PDF notices; the project API rejects drafts from new generation requests. Review, Optimize and client reports remain available.
