# Provenance

## Event-written work

| Date | Work | Source |
| --- | --- | --- |
| 2026-10-27 | Fresh workspace, app/service boundaries, build setup, documentation | Newly authored in this repository |
| 2026-10-27 | Explicit Mac cmux connection helper and device setup notes | Newly authored; no connection credentials included |
| 2026-10-27 | React/CSS initial app screens, local preview interactions, SwiftUI screens, named iOS color/image assets | Newly implemented from the supplied Figma design; no prior product code copied |
| 2026-10-27 | Replacement KMM scaffold, shared Compose screens, Ktor API and native launch fix | Newly authored; replaces the initial stack |
| 2026-10-27 | Illustrated sign-in and supplied Plotus logo/app icon | New implementation using pre-existing Figma and Mobbin references in DESIGN.md |
| 2026-10-27 | Separate Go auth service, Google/session/PKCE/WebAuthn contracts and isolated local setup | Newly authored here; no private auth source imported |
| 2026-10-27 | Optional private engine gitlink and service-boundary documentation | New metadata; engine code and CAD assets are pre-existing and private |

Do not backdate commits. Only event-written code belongs in public submission source. The optional private submodule is an owner-development pointer, not event-written engine code.

## Prior inputs and third-party dependencies

- Plotus Figma file `0AIbJahPpgu09bT3AqguPx`, page `349:144623` (`App design clean`): supplied visual design and UI icon exports. Pre-existing input, not event-created work.
- Kotlin, Compose Multiplatform, Ktor, kotlinx.serialization and Gradle are third-party dependencies, pinned in Gradle configuration. The Gradle wrapper is generated from the official distribution with a pinned distribution checksum.
- Plus Jakarta Sans and Hanken Grotesk from [Google Fonts](https://github.com/google/fonts): third-party fonts under the bundled SIL Open Font License files. Shared Compose resources include static Regular/SemiBold/Bold instances generated from the supplied variable Plus Jakarta Sans font.
- Shared light/dark PNG icons are direct 3x raster conversions of the supplied Figma SVG exports. Canonical node references and original asset checksums are in `design-sources.json`.
- Logo review board `463:18`, primary symbol `463:21`, dark symbol `463:24`, app icon `463:31`: supplied pre-existing Figma assets. Raster resources derive directly from the vectors.
- No private product source, private datasets, generated plans or service credentials are included in public commits. The private engine gitlink does not publish its source.

## Hosted services

No private hosted engine is connected to the app. `services/engine-private` references private `baymac/plotus-engine`, an unchanged extraction of the existing Python package and required CAD assets from source dated 23 October 2026. Runtime version: 2.3.91. The private repo records exact file hashes; its HTTP transport/packaging was written on 27 October 2026. The engine itself is prior work. [ENGINE.md](ENGINE.md) documents optional source setup and the future backend-only boundary. App and auth builds do not require private access.

## Stack correction

The initial event-written React/SwiftUI implementation remains in earlier commits for an honest work history. It was removed from the current tree and replaced with Kotlin Multiplatform models and shared Compose UI for iOS, web and an optional JVM preview. The API scaffold was rewritten in Kotlin/Ktor. `/Users/parichay/dev/naksha` on the user’s Mac was read for toolchain conventions only; no private source was copied.
