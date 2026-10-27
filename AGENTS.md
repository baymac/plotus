# Plotus

- This is a fresh hackathon repository. Write new code here; do not copy code, Git history, generated plans, datasets, or secrets from existing private products.
- Keep README provenance current. Prior design inputs and any future external hosted service must be explicitly identified.
- Figma's `App design clean` page is the visual source. Implement only the current milestone and document deferred flows.
- All app UI and state must use Kotlin Multiplatform and Compose Multiplatform, shared between iOS and web. Do not introduce React, TypeScript UI, or a separate SwiftUI implementation. A minimal UIKit host is allowed for the iOS executable.
- Shared models and validation live in `shared`; shared screens live in `composeApp/commonMain`; the backend uses Kotlin/Ktor in `services/api`.
- Never simulate authenticated sessions. The design preview is local sample data and must stay separate from future real accounts.
- Run `./gradlew :shared:jvmTest :server:test :composeApp:compileKotlinJvm :composeApp:wasmJsBrowserDistribution` before committing. Native builds need macOS/Xcode; report them accurately.
