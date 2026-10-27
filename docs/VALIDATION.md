# Initial scaffold validation — 27 October 2026

## Completed

- TypeScript checks, production web build, and API tests.
- Chromium browser checks: sign-in does not create a session, explicit preview entry, search/empty state, sorting, sidebar preference persistence, project create/edit/delete, conditional client fields, theme switching, General settings, Escape dismissal, and no horizontal document overflow at 320, 390, 768, 1024 and 1440 pixels.
- Browser requests for bundled UI assets succeeded; no page runtime errors in those flows.
- Visual review against the supplied Figma references for desktop Projects, mobile Projects, desktop sign-in, mobile project form and dark Projects. Colors, fonts, icon assets and main dimensions follow the referenced frames.
- iOS plist parses; referenced fonts and adaptive image resources exist and are nonempty. Mac helper passes Bash syntax validation.
- Public GitHub repository starts with new history; no existing product code, private data or credentials were imported.

## Limits

- Chromium checks cover web rendering, not native Safari, VoiceOver, Dynamic Type, native authentication, or physical-device behavior.
- SwiftUI sources have not been compiled or run yet. Linux resource checks are not an Xcode build. Native tabs, lists and forms intentionally use system presentation; a Mac render comparison remains necessary.
- No live authentication, data persistence, plot editor, generation jobs, or Meta AI integration is implemented.
- The saved cmux relay was offline. No live Mac socket has been supplied, so Xcode, signing, the connected iPhone and native layout remain unchecked.
- CI is a template until an authorized GitHub account installs the workflow; checks currently run locally.
