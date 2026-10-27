# Provenance

## Event-written work

| Date | Work | Source |
| --- | --- | --- |
| 2026-10-27 | Fresh workspace, app/service boundaries, build setup, documentation | Newly authored in this repository |
| 2026-10-27 | Explicit Mac cmux connection helper and device setup notes | Newly authored; no connection credentials included |
| 2026-10-27 | React/CSS initial app screens, local preview interactions, SwiftUI screens, named iOS color/image assets | Newly implemented from the supplied Figma design; no prior product code copied |

Do not backdate commits. Update this log as functionality is added. Only code authored during the event belongs in this submission.

## Prior inputs and third-party dependencies

- Plotus Figma file `0AIbJahPpgu09bT3AqguPx`, page `349:144623` (`App design clean`): supplied visual design and UI icon exports. Pre-existing input, not event-created work.
- Kotlin, Compose Multiplatform, Ktor, kotlinx.serialization and Gradle are third-party dependencies, pinned in Gradle configuration. The Gradle wrapper is generated from the official distribution with a pinned distribution checksum.
- Plus Jakarta Sans and Hanken Grotesk from [Google Fonts](https://github.com/google/fonts): third-party fonts under the bundled SIL Open Font License files. Shared Compose resources include static Regular/SemiBold/Bold instances generated from the supplied variable Plus Jakarta Sans font.
- Shared light/dark PNG icons are direct 3x raster conversions of the supplied Figma SVG exports. Canonical node references and original asset checksums are in `design-sources.json`.
- No private product source, private datasets, generated floor plans, or service credentials are included.

## Hosted services

None connected in the scaffold. Any later private engine integration must be disclosed here and in the root README, including the API boundary and event-written code paths.

## Stack correction

The initial event-written React/SwiftUI implementation remains in earlier commits for an honest work history. It was removed from the current tree and replaced with Kotlin Multiplatform models and shared Compose UI for iOS, web and an optional JVM preview. The API scaffold was rewritten in Kotlin/Ktor. `/Users/parichay/dev/naksha` on the user’s Mac was read for toolchain conventions only; no private source was copied.
