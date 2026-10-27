# Validation — 27 October 2026

## Shared app

- `./gradlew :shared:jvmTest :server:test :composeApp:compileKotlinJvm :composeApp:wasmJsBrowserDistribution` passed for the illustrated Google/passkey version. Four shared JVM checks and the Ktor API boundary check pass.
- Kotlin/Wasm development webpack also passed, run separately from production distribution. Combining both webpack variants in one Gradle invocation triggers an upstream shared-output task validation conflict; the documented production command passes.
- Chromium renders were reviewed at 390×844 and 375×667 mobile sizes and 1440×900 desktop size, including light/dark variants. Google appears above the secondary passkey button, the hero and two-line headline occupy the upper section, and demo entry remains separate. No JavaScript page errors were observed.
- Native Kotlin framework linking and signed Xcode packaging passed on the user's Mac. The illustrated Google/passkey build was installed and launched on the paired iPhone 13 mini. The device console remained attached without a launch exception. This confirms packaging/launch, not real provider sign-in.
- The first scaffold launch required the Compose `CADisableMinimumFrameDurationOnPhone` plist key; the committed host includes it.
- The original private Mac checkout was not modified. Build/installation used `/Users/parichay/dev/plotus`.

## Separate auth service

- Go build, `go vet ./...` and seven unit/integration checks pass. Integration checks use the newly created Plotus auth PostgreSQL/Redis stack, with test accounts cleaned afterward.
- Checks cover the RFC 7636 PKCE vector, JWT algorithm/issuer/audience/expiry/key rejection, redirect allowlisting, single-use native grants, hashed refresh storage, refresh rotation, CSRF/origin protection, revocation and rejection of unbound/forged passkey assertions.
- The auth Docker image builds and starts with its own schema and signing key. `/health`, `/api/auth/config` and JWKS return 200. WebAuthn begin returns a challenge requiring user verification.
- Google remains disabled because Plotus OAuth credentials/callback registration are not configured. Successful Google OAuth and a real passkey assertion/enrollment have not been tested. Native passkeys also require a deployed RP domain/associated-domain configuration.
- Shared client OS-browser, secure-store and live-session wiring remain pending. The design preview never creates an authenticated session.

## Private engine extraction

- All 210 copied engine files match the original source hashes. Runtime engine version 2.3.91 imports successfully with its bundled CAD assets.
- The private Python service Docker image builds and is healthy on loopback port 4002. HTTP checks verify health, missing-credential rejection and invalid-payload rejection. No solver/placement generation was executed.
- Public Git records only a mode-160000 submodule pointer. Private engine source, credentials, generated plans and old product history are absent from public commits. Gradle and auth do not require submodule initialization.

## Local checks only

No CI or workflow template is included, as requested. Backend project persistence, plot editing, client authentication integration, engine forwarding and Meta AI integration remain subsequent work.
