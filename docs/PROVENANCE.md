# Sources and credits

## Created during the hackathon

The public app, shared iPhone and web screens, supporting services and welcome illustration were created during the event on 27–28 October 2026. The miniature home on the welcome screen is an illustration, not a generated floor plan.

## Existing design and planning technology

The interface and logo use the supplied [Plotus Figma design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623), which predates the event. [Design references](DESIGN.md) and the [asset source record](design-sources.json) identify the inputs.

The planning engine and its CAD assets are pre-existing private technology. They remain in the private `baymac/plotus-engine` project, referenced through an optional private submodule. Public app builds do not need access to that source. The engine's Python service wrapper was created during the event; the event-authored project API now calls it for buildable-envelope preview and checked generation. The event-authored transport renders accepted engine sheets into private account-owned previews. Event-authored integration corrections add the optional-room presence metadata required by the native validator and a bounded, fully revalidated adjustment of automatically inferred internal door positions. They do not replace the pre-existing solver or relax its acceptance checks. [About the planning engine](ENGINE.md).

## Third-party assets and software

- Plus Jakarta Sans and Hanken Grotesk are from [Google Fonts](https://github.com/google/fonts), with their bundled SIL Open Font License files.
- Interface icons and the Plotus mark are local exports of the supplied design. Their source references and checksums are in the asset record.
- The Facebook sign-in icon is adapted from [Meta’s Android SDK](https://github.com/facebook/facebook-android-sdk/blob/main/facebook-common/src/main/res/drawable/com_facebook_button_icon.xml), with its [license](../third-party/facebook-sdk-license.txt).
- The app and services use open-source libraries identified in their dependency files.
- The web-only 3D viewport was added after the hackathon on 29 October at the user's request. It uses React, React Three Fiber, Drei and Three.js, pinned in `apps/plan-viewer/package-lock.json`. Its conceptual meshes and furniture primitives are newly authored from the accepted engine geometry. [Maket's 2D-to-3D workflow](https://www.maket.ai/features/2d-to-3d) informed view switching and camera controls; none of its source, project data, textures or models are included. The private service's new scene exporter does not replace the existing solver or its acceptance checks.
- The event-authored drawing presenter and export flow use the existing engine's generated geometry. [GNU LibreDWG](https://www.gnu.org/software/libredwg/) writes CAD files as a separate private server tool; Tesseract reads printed area labels from older saved images locally. Their implementations predate the event. No generated plans or third-party source are added to the public repository.

- The marketing website uses the existing Plotus mark and fonts, newly written copy and SVG concept studies, and an original AI-generated courtyard-home illustration. Architecture/product references and asset details are recorded in [the marketing site README](../apps/marketing/README.md). The illustrations are not generated app results. Cloudflare Pages serves the website and web app, Email Routing forwards its public contact address, and Cloudflare Tunnel carries requests to the newly written public auth and project services.

Private product source, customer data, generated plans and credentials are not included in the public app source.

Sketch-image selection, the review flow and the authenticated Meta vision adapter were written during the event. The configured image-understanding model is an external hosted service, not event-authored model code. Images are processed by the provider; its source and credentials are outside this repository.

Architect Review, its shared Compose screens, owner-scoped review/optimization APIs and Meta explanation adapter were added on 1 November 2026 after the hackathon. The user-approved Figma additions on App design clean are their visual source. The deterministic metrics, archive and runtime soft-objective adapter are new additions around the pre-existing private solver. The hosted Meta model interprets structured findings; it does not produce measurements, compliance results or geometry. No model source or credentials are included.

The multi-page client PDF renderer and Auto V2 integration were authored on 3 November 2026. The report uses PyMuPDF/CairoSVG and deterministic projection of the already accepted portable scene; no model creates its geometry. The private pre-existing planning solver remains authoritative.

The 4 November review refinement adds concise AI priorities with explicit next steps, one measured improvement goal per app request, targeted source-model preferences and measured goal acceptance. These are post-event additions around the pre-existing engine. New generation checks use frozen source and empty prior directories; a saved example or an unsuccessful bounded search is never reported as a fresh accepted generation.

The 4 November generation-recovery experiment was withdrawn at the owner's request. The native generation code and worker path were restored to their pre-experiment revision. Existing saved drafts remain explicitly labeled for historical accuracy. The review, optimizer and client-report refinements are retained.
