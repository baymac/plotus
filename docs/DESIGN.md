# Initial design implementation

Source: [Plotus / App design clean](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623), inspected on 27 October 2026. This is a pre-existing design input; the app code is newly written during the event.

| Screen | Mobile Light | Desktop Light | Dark references |
| --- | --- | --- | --- |
| Projects | `364:29969` | `364:30853` | `364:30411`, `364:31376` |
| Sign in | `364:30156` | `364:31065` | `364:31588` |
| General settings | `364:30081` | `364:30962` | Semantic palette from Projects |
| New project | `364:30176` | `364:31087` | Semantic palette from Projects |
| Client project form | `364:30286` | Same form at desktop drawer width | Semantic palette from Projects |

One Compose Multiplatform implementation in `composeApp/src/commonMain` supplies both the iOS and web UI. The semantic light/dark palette, Plus Jakarta Sans fonts, shared controls, layout breakpoints and supplied icons are common resources. Original SVG exports remain under `assets/design`; the shared image resources are direct 3x raster conversions for consistent cross-platform loading.

Desktop uses a 264px sidebar, 72px top bar and 32px content inset. Narrow layouts use the supplied mobile header and two-destination bottom navigation. Forms use Compose dialogs with keyboard focus management, field validation, conditional client details and deletion confirmation.

The only authored Swift code is the iOS lifecycle host for the exported Compose UIViewController. There are no separate SwiftUI or React screen implementations. Theme and sidebar preferences use tiny platform-specific storage adapters; app data and validation are common Kotlin.

The explicit preview entry is a scaffold addition for reviewing local sample data without authentication. Account settings beyond the preview notice, passkey enrollment, plot editing and generation are deferred.

## Sign-in refinement

The user-supplied welcome-screen image is the composition reference: a large friendly hero, a short two-line headline and authentication actions anchored near the bottom. It supersedes the earlier compact centered form. Plotus keeps its warm paper, terracotta and sage palette, supplied mark and type family.

The shared screen uses a new editable Compose Canvas illustration of a miniature home resting on planning paper. It is newly drawn code, not engine output or an extracted private floor plan. Mobile places the headline “Make room for your next idea.” under the illustration and keeps Google above a secondary passkey action at the bottom. Desktop gives the illustration and headline their own pane beside the sign-in controls. Short screens can scroll. Both themes use the same structure.

Only Google and passkeys are offered. “View demo” remains a separate local preview, never an authenticated session. The reference's purple bed, blue selection box, Apple option and unconfigured legal links are not part of Plotus.

Earlier Mobbin research informed button clarity and restrained copy:

- [Linear Mobile login](https://mobbin.com/screens/edac1658-5f94-4d9c-8be1-46eef50fbe09): clear vertical provider choices; retain hierarchy without Linear branding or email/SSO expansion.
- [Linear web login](https://mobbin.com/screens/3997d60f-bea7-4068-af79-3942a664072e): a readable narrow action column on desktop, adapted to the illustrated split layout.
- [Notion login](https://mobbin.com/screens/ef4aba01-ca49-4180-9ef3-96c3fc6be970): concise provider labels; additional methods and verification fields are outside this screen.

The supplied logo is from review board `463:18`, primary `463:21`, dark `463:24`, and app-icon treatment `463:31`. It is bundled locally; no expiring Figma URL is needed at runtime.
