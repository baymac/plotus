# Plotus API

The Kotlin/Ktor service stores each account's projects in its own PostgreSQL database. It verifies every project request with the separate auth service, including session revocation. Project ownership comes from that verified account, never from request data.

Start the local stack with `./docker-local/init.sh`, then `cd docker-local && docker compose up --build -d`. The app reaches `/v1/` through the web gateway. iPhone uses the same gateway as sign-in, so its existing HTTPS tunnel address works for both.

The project endpoints require a Plotus bearer token:

- `GET /v1/projects` lists the current account's projects, most recently edited first.
- `POST /v1/projects` creates a project from `{"details": {"name": "...", "location": "..."}}`. Client fields follow the shared `ProjectDraft` model.
- `GET /v1/projects/{id}` reads an owned project.
- `PUT /v1/projects/{id}` updates it with `{"details": {...}, "revision": 1}`.
- `DELETE /v1/projects/{id}` deletes it with `{"revision": 1}`.

Use the revision returned by the service to prevent overwriting changes from another device. Unknown or unowned project IDs return 404. Invalid details return 400; stale revisions return 409. Request bodies are limited to 16 KiB. Project data and credentials are not logged.

Run `./services/api/scripts/check.sh` from the root after starting the local project database. It runs shared tests, API tests against PostgreSQL, the JVM client compilation and the production web build. Test data is isolated by random account IDs and cleaned up afterwards. Test identities are confined to the test application; no development sign-in route is added.

## Welcome project

The first verified API request initializes a new workspace from its configured welcome template. This creates a normal project and a completed generation owned by that account, with fresh project, generation and request IDs. Concurrent requests cannot duplicate it. The onboarding record survives deletion, and existing workspaces retain their own projects. No owner, project or generation identifier from a client request controls this setup.

An operator installs the private, immutable template by setting `PLOTUS_DEMO_SOURCE_PROJECT_ID` and `PLOTUS_DEMO_SOURCE_GENERATION_ID` in the backend environment. `PLOTUS_DEMO_TEMPLATE_KEY` defaults to `welcome-v1`. The API reads the completed generation's saved brief, requires its measured hard checks to pass and stores only geometry, requirements and drawings in PostgreSQL. The demo uses a generic project name and location, without the source project's client or contact details. Later edits or deletion of the source do not change installed templates. Use a new template key for a deliberately updated example. With no installed template, projects continue to work normally.

The saved generation snapshot supplies the demo's plot and floor-plan requirements together. PDF export uses that same owned snapshot, and Generate starts the usual checked native job; onboarding does not run or bypass the planner. Configure the private engine's `PLOTUS_ENGINE_DEMO_PRIOR_ID` and `PLOTUS_ENGINE_DEMO_ANALYSIS_ID` with the approved example's programme ID and immutable analysis ID so an exact copy can use its native layout as starting advice. The complete saved request must match apart from the project identifier; every fresh solve and native acceptance check still runs.

`WelcomeProjectsIntegrationTest` covers concurrent initialization, separate ownership, source edits, deletion, missing and unchecked templates, PDF transport and new generation. `RealWelcomeGenerationTest` is an opt-in check against the actual private worker: provide a private `PLOTUS_TEST_DEMO_SOURCE_FILE` containing `brief` and `generation`, plus `PLOTUS_TEST_ENGINE_URL` and `PLOTUS_TEST_ENGINE_TOKEN`. It saves real demo and fresh-generation PDFs to `PLOTUS_TEST_DEMO_OUTPUT` (default `/tmp/plotus-welcome-verification`). Reference data, generated reports and credentials must stay outside Git.
