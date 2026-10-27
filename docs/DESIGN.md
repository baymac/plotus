# Initial design implementation

Source: [Plotus / App design clean](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623), inspected on 27 October 2026. This is a pre-existing design input; the app code is newly written during the event.

| Screen | Mobile Light | Desktop Light | Dark references |
| --- | --- | --- | --- |
| Projects | `364:29969` | `364:30853` | `364:30411`, `364:31376` |
| Sign in | `364:30156` | `364:31065` | `364:31588` |
| General settings | `364:30081` | `364:30962` | Semantic palette from Projects |
| New project | `364:30176` | `364:31087` | Semantic palette from Projects |
| Client project form | `364:30286` | Same form at desktop drawer width | Semantic palette from Projects |

Web uses semantic CSS tokens, Plus Jakarta Sans, and Hanken Grotesk for General settings. The desktop shell has a 264px sidebar, 72px top bar and 32px content inset. Collapsing the sidebar takes 180ms and retains the preference across pages. Reduced-motion preferences disable the transition. Mobile uses a 76px header and two-tab bottom navigation, with safe-area padding.

New/Edit project is a native HTML dialog: focus is contained by the browser, Escape dismisses it, fields retain their values while open, and Save/Create validates required fields. The desktop drawer is 440px; mobile uses a bottom sheet. Destructive deletion requires confirmation. Theme follows the OS unless overridden from the desktop account menu.

iOS adapts the source to native SwiftUI TabView, NavigationStack, List, Form, sheets and confirmation dialogs, retaining the product's color and typography tokens. The sign-in content scrolls when larger text or a shorter display needs more room. Native asset catalogs have independent light/dark values.

The explicit design preview link is a scaffold addition so reviewers can inspect sample data without authentication. Account settings beyond the preview notice, passkey enrollment, plot editing, and planning/generation workflows are deferred. Project rows open metadata editing until the planning editor is added.
