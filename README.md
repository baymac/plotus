# Plotus

Kotlin Multiplatform app for iOS and web, with one shared Compose Multiplatform UI and shared Kotlin models. The backend scaffold also uses Kotlin (Ktor).

Fresh public submission repository for the [Meta Global AI Developer Hackathon](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/details), started on 27 October 2026. No private product source or Git history is imported.

## Run

Install JDK 17. Use the checked-in Gradle wrapper; a separate Gradle installation is unnecessary.

```sh
# Web app (Kotlin/Wasm + Compose), http://localhost:8081
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Backend, in another terminal, http://localhost:4000/health
./gradlew :server:run

# Optional desktop JVM preview using exactly the same shared screens
./gradlew :composeApp:run
```

The web build downloads its own Node/Yarn tooling through Gradle. There is no authored React/TypeScript client and no npm setup step.

```sh
./gradlew :shared:jvmTest :server:test :composeApp:compileKotlinJvm
./gradlew :composeApp:wasmJsBrowserDistribution
```

Web distribution: `composeApp/build/dist/wasmJs/productionExecutable/`.

## iOS

On a Mac with Xcode, JDK 17+ and XcodeGen:

```sh
cd iosApp
xcodegen generate
open Plotus.xcodeproj
```

Choose a development team in Signing & Capabilities and select the connected iPhone. The Xcode build phase builds and links the shared Kotlin framework. `iosApp/Sources/AppDelegate.swift` is only a UIKit lifecycle entry point; all screens, app state, design tokens and validation are shared Kotlin. No SwiftUI screens are used.

See [Mac testing](docs/MAC-TESTING.md) for the cmux connection.

## Layout

- `composeApp/src/commonMain` — shared Compose screens, theme, controls and bundled resources.
- `composeApp/src/wasmJsMain` — browser entry point and local preference storage.
- `composeApp/src/iosMain` — Compose UIViewController entry point and iOS preferences.
- `composeApp/src/jvmMain` — optional desktop preview entry point.
- `shared` — Kotlin models, project validation and preview operations; shared by every client and the server.
- `iosApp` — minimal iOS host and XcodeGen configuration.
- `services/api` — Kotlin/Ktor API scaffold.
- `services/auth` — reserved authentication integration boundary.
- `assets/design` — supplied Figma SVG exports, retained as the source for shared image resources.

## Current milestone

Shared screens cover sign-in UI, Projects, General settings, New/Edit project forms with optional client details, delete confirmation, search, sorting and light/dark themes. Desktop has a collapsing sidebar; narrow layouts use two bottom destinations. Project rows edit metadata until the planning editor is added.

Choose **Explore design preview** on the sign-in screen to review local sample projects. Google and passkey buttons do not authenticate. Preview projects exist only in memory; only appearance and sidebar preferences persist. API `/v1/*` routes return `501` instead of sample account data.

Live auth, backend persistence, plot editing, floor-plan generation and Meta AI integration are subsequent milestones. This is an initial scaffold, not a completed event submission. Check [validation](docs/VALIDATION.md) for the current build/device evidence.

## Event work and prior inputs

All application source here was newly written during the event. Commit history includes an initial React/SwiftUI implementation that was replaced after the stack correction; the current implementation is Kotlin Multiplatform with shared Compose UI.

The [supplied Plotus Figma design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623) is a pre-existing input, not event-created design work. Exported icons and third-party fonts are disclosed in [provenance](docs/PROVENANCE.md).

The existing private Plotus/Naksha product, source, planning engine, generated plans, datasets and credentials are excluded. The Mac checkout was inspected only to confirm build/toolchain conventions. No private hosted service is connected. If one is integrated later, document the service and distinguish the event-written integration from its pre-existing implementation. Fresh Git history alone does not establish eligibility.

The CI configuration is a template under `docs/ci/` because the available GitHub token cannot create workflow files. Local Gradle checks remain available.
