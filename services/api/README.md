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

Run `./services/api/scripts/check.sh` from the root after starting the local project database. It runs shared tests, API tests against PostgreSQL, the JVM client compilation and the production web build. Test data is isolated by random account IDs and cleaned up afterwards. Tests never add a development sign-in route or return sample projects to a real account.
