# First milestone

Start with the provided design's sign-in, Projects, General settings, and New project screens. Use a clearly separate local preview to review the interface before accounts exist.

1. Commit the independent scaffold with a lockfile and build instructions.
2. Implement the first web and native iOS screens against Figma, with semantic light/dark tokens and accessible controls.
3. Add one auth service for both clients: provider callback validation, session handling, sign-out, then passkeys. Pick the provider before implementing real auth.
4. Add authenticated project storage and migrations. Keep ownership enforcement on the server.
5. Implement Plot setup → Floor plan → Generation as a vertical slice, including the event's Meta AI integration and any explicitly disclosed private service boundary.
6. Test the native app on the connected iPhone and desktop web in a browser; document device and build results.

Avoid expanding into all finite prototype states before the first complete journey works. Native desktop packaging can follow once the web and iOS clients share the API.

The shared ChatGPT office-hours link was inaccessible during setup. The event page was read on 27 October 2026. The user-supplied standalone-repository requirement governs this repository; the accessible event details do not independently establish that clarification.
