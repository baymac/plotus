# Sources and credits

## Created during the hackathon

The public app, shared iPhone and web screens, supporting services and welcome illustration were created during the event on 27 October 2026. The miniature home on the welcome screen is an illustration, not a generated floor plan.

## Existing design and planning technology

The interface and logo use the supplied [Plotus Figma design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623), which predates the event. [Design references](DESIGN.md) and the [asset source record](design-sources.json) identify the inputs.

The planning engine and its CAD assets are pre-existing private technology. They remain in the private `baymac/plotus-engine` project, referenced through an optional private submodule. Public app builds do not need access to that source. The engine's Python service wrapper was created during the event; the app does not call it yet. [About the planning engine](ENGINE.md).

## Third-party assets and software

- Plus Jakarta Sans and Hanken Grotesk are from [Google Fonts](https://github.com/google/fonts), with their bundled SIL Open Font License files.
- Interface icons and the Plotus mark are local exports of the supplied design. Their source references and checksums are in the asset record.
- The Facebook sign-in icon is adapted from [Meta’s Android SDK](https://github.com/facebook/facebook-android-sdk/blob/main/facebook-common/src/main/res/drawable/com_facebook_button_icon.xml), with its [license](../third-party/facebook-sdk-license.txt).
- The app and services use open-source libraries identified in their dependency files.

- The marketing website uses the existing Plotus mark and fonts, newly written copy and SVG concept studies, and an original AI-generated courtyard-home illustration. Architecture/product references and asset details are recorded in [the marketing site README](../apps/marketing/README.md). The illustrations are not generated app results. Cloudflare Pages and Email Routing host the site and forward its public contact address.

Private product source, customer data, generated plans and credentials are not included in the public app source.
