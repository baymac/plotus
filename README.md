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

Scaffold and first design implementation. Live authentication, backend persistence, plot editing, floor-plan generation, and a native desktop binary are subsequent milestones. The desktop deliverable currently means the web application. The sample workspace is a design preview, separate from authentication.

See `docs/START-HERE.md` for the implementation sequence and `docs/PROVENANCE.md` for the work log.
