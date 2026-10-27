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
