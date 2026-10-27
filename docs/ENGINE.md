# Optional private engine

Only the pre-existing planning engine and its required CAD assets are private. The app, domain API, separate auth service and integration documentation belong to this public repository.

| Component | Repository / path | Provenance |
| --- | --- | --- |
| Planning engine | Private `baymac/plotus-engine`, `engine/` | Existing Python code, extracted unchanged from 23 October 2026 source. Runtime version 2.3.91. |
| Engine HTTP wrapper | Same private repo, `service/` | Written on 27 October 2026; calls the existing engine without solver changes. |
| Optional source pointer | `services/engine-private` | Git submodule pinned to a private commit. Source is not published here. |
| Shared app, Ktor API, Go auth | This public repo | Newly authored during the event. |

## Public checkout

```sh
git clone https://github.com/baymac/plotus.git
cd plotus
./gradlew :shared:jvmTest :server:test :composeApp:compileKotlinJvm
```

Do not add `--recurse-submodules` to public setup or CI. Gradle, auth and design preview work with the private submodule absent. Source archives do not need its contents. The scaffold does not yet call the engine or provide floor-plan generation.

## Owner development

With authorized GitHub access to the private repository:

```sh
git submodule update --init services/engine-private
cd services/engine-private
cp .env.example .env
# Set a random PLOTUS_ENGINE_SERVICE_TOKEN in the ignored .env.
docker compose up --build -d
curl http://localhost:4002/health
```

The private repo records hashes for every extracted file, its original commit and asset provenance. Source history, other product services, user data, generated plans and secrets were not extracted. Original product checkouts remain unchanged.

## Hosted integration contract

The Ktor backend will call a separately hosted engine using a server-only service credential. Keep `PLOTUS_ENGINE_URL` and `PLOTUS_ENGINE_SERVICE_TOKEN` in deployment secrets; never bundle the token in Kotlin/Wasm, iOS or desktop clients. These names describe the future adapter; no public API route currently forwards to the engine.

| Endpoint | Request / result |
| --- | --- |
| `GET /health` | Version/readiness; no generation. |
| `POST /v1/programmes/resolve` | JSON `programme`; compiles requirements without placement. |
| `POST /v1/plans/generate` | JSON `programme`, optional `search_seconds`, `deterministic_limit`, `seed`, `output_stage`; original plan/report. |

POSTs require `Authorization: Bearer <service token>`. The wrapper permits one site attempt and one architectural attempt, one worker at a time, and a 240-second process limit. Invalid programmes return 422; busy returns 429; timeout returns 504 with no accepted plan. No automatic solve retry is introduced. Use TLS/private networking between backend and remote engine.

## Event disclosure

The engine and CAD corpus predate the event. A fresh extraction commit does not make them event-written. Public submission code does not contain them; the optional gitlink is owner-development metadata. If the submitted app uses this capability, disclose the pre-existing hosted engine and identify the new integration in the README. Reviewers must not need private repo access. This packaging does not establish event eligibility.
