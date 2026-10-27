# Plotus

An iOS app and desktop web app for creating and comparing floor-plan concepts.

Fresh public submission repository for the [Meta Global AI Developer Hackathon](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/details). This repository starts with new Git history on 27 October 2026. No existing product source or Git history is imported.

## Run

Requires Node 22.12+ and npm.

```sh
npm ci
npm run dev
# In another terminal:
npm run dev:api
```

Web: http://localhost:5173. API health: http://localhost:4000/health.

Choose **Explore design preview** on the sign-in screen to review the local sample workspace. Google and passkey buttons do not authenticate yet. Preview projects live only in memory; reloading restores the fixtures. Only appearance and sidebar preferences are saved locally.

```sh
npm run check
npm run build
npm test
```

## Layout

- `apps/web` — React/TypeScript desktop and responsive web app.
- `apps/ios` — native SwiftUI iOS application; see its README for Mac/device setup.
- `services/api` — API boundary and health endpoint.
- `services/auth` — reserved authentication integration boundary.
- `packages/contracts` — shared HTTP data contracts.
- `docs` — design source, provenance, and milestone notes.
- `scripts` — development and Mac connection tools.

## Event work and prior inputs

All application source in this repository is newly written for this event. The initial scaffold is dated 27 October 2026. Commit history records subsequent event work.

The [provided Plotus Figma design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623) is a pre-existing design input. It is not claimed as event-created design work. Exported UI icons and licensed fonts are identified in `docs/PROVENANCE.md`.

The existing private Plotus/Naksha product, planning engine, generated plans, datasets, infrastructure, and source code are excluded. This scaffold does not connect to any private hosted service. If one is integrated later, this README must name the service, explain its role, and distinguish the event-written integration from the pre-existing service. Fresh Git history alone does not establish eligibility.

## Current milestone

Implemented for web: sign-in UI, Projects, General settings, New/Edit project forms with optional client details, delete confirmation, search, sorting, and light/dark themes. The desktop sidebar collapses and remembers its state. Mobile web uses the supplied two-tab layout. Project rows open metadata editing in this milestone; the planning editor comes next.

The iOS source implements the same initial journey with SwiftUI, native tabs, lists and forms, adaptive colors, bundled vector icons and fonts. Its native layout is adapted to system navigation and safe areas. A native build and device validation are still pending a Mac connection. See [Mac testing](docs/MAC-TESTING.md).

Live authentication, backend persistence, plot editing, floor-plan generation, Meta AI integration, and a native desktop binary are subsequent milestones. The API currently exposes health only; reserved `/v1/*` routes return `501`. The desktop deliverable currently means the web application. This is a starting scaffold, not a finished hackathon submission.

The checks pass locally. A GitHub Actions template is saved in `docs/ci/github-actions.yml`; the available GitHub token cannot create workflow files. Move it to `.github/workflows/check.yml` using an account or token with workflow permission to enable CI.

See `docs/START-HERE.md` for the implementation sequence and `docs/PROVENANCE.md` for the work log.
