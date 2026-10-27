# Local development

From the project root:

```sh
./services/auth/scripts/init-local.sh
cd docker-local
docker compose up --build -d
```

Open the web app at **http://localhost:8081**. API: port 4000; auth: 4001. PostgreSQL and Redis are available only on loopback ports 5441 and 6381.

Put local Google credentials in the ignored `.env`. The registered callback is `http://localhost:4001/api/auth/login/google/callback`. Rebuild a changed app/service with `docker compose up --build -d web` (or `api` / `auth`).

For Facebook, set its app ID/secret and exact registered HTTPS `FACEBOOK_CALLBACK_URL` in `.env`. A development tunnel can receive that callback while the web app stays on localhost. Update both Meta and `.env` when its address changes.

App code lives in `apps/client` and `apps/ios`; services live in `services`; shared Kotlin models live in `packages/shared`. iOS builds run in Xcode on the Mac.

The private engine is optional and excluded from default startup. Owners can initialize `services/engine-private`, set its service token, and explicitly enable the `engine` profile.
