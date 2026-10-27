# Plotus

Kotlin Multiplatform app for iOS and web, with shared Compose UI and Kotlin models, a Kotlin/Ktor domain API, and a separate Go auth service.

Fresh public submission repository for the [Meta Global AI Developer Hackathon](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/details), started on 27 October 2026. Private product source and history are excluded from public commits. The pre-existing engine is referenced only by an optional private submodule.

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
- `services/auth` — separate Go auth service with Google/OIDC, browser sessions, native PKCE exchange and passkeys.
- `services/engine-private` — optional private Python engine submodule; public builds do not initialize or require it.
- `assets/design` — supplied Figma SVG exports, retained as the source for shared image resources.

## Current milestone

Shared screens cover sign-in UI, Projects, General settings, New/Edit project forms with optional client details, delete confirmation, search, sorting and light/dark themes. Desktop has a collapsing sidebar; narrow layouts use two bottom destinations. Project rows edit metadata until the planning editor is added.

Choose **View demo** on the sign-in screen to review local sample projects. Google and passkey actions still need client/session configuration. Preview projects exist only in memory; only appearance and sidebar preferences persist. API `/v1/*` routes return `501` instead of sample account data.

The separate auth service owns its database, Redis and signing key; see [auth setup](services/auth/README.md). Google configuration, the passkey RP domain and client session integration are still required for live sign-in. Backend persistence, plot editing, generation and Meta AI integration are subsequent milestones. Check [validation](docs/VALIDATION.md) for build/device evidence.

## Event work and prior inputs

The public app, domain API and auth source were newly written during the event. Commit history includes an initial React/SwiftUI implementation replaced after the stack correction; current app UI is Kotlin Multiplatform with shared Compose.

The [supplied Plotus Figma design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623) is a pre-existing input, not event-created design work. Exported icons and third-party fonts are disclosed in [provenance](docs/PROVENANCE.md).

The pre-existing Python engine and required CAD assets were extracted unchanged to [private `baymac/plotus-engine`](https://github.com/baymac/plotus-engine), referenced through optional `services/engine-private`. Public commits contain only its URL and commit pointer. Ordinary clones, app builds and auth setup work without private access. See [engine boundaries and service contract](docs/ENGINE.md).

The engine predates the event; its private HTTP wrapper was written during the event. No app-to-engine integration is connected yet. Other private product code, generated plans, databases and credentials remain excluded. Submitted use of a hosted engine must disclose that dependency and identify the new integration code. Fresh Git history alone does not establish eligibility.
