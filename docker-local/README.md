# Local development

From the project root:

```sh
./docker-local/init.sh
cd docker-local
docker compose up --build -d
```

Open **http://localhost:8081** for the landing page or **http://localhost:8081/projects** for the signed-in app. Settings uses `/settings`, `/settings/general` and `/settings/account`; signed-out visitors go to `/login`. The gateway also listens on loopback port 4001 for existing OAuth callbacks and iPhone testing; it routes both auth and project requests internally. The API is available on port 4000. The separate project and auth databases use loopback ports 5440 and 5441; Redis uses 6381.

Put local Google credentials in the ignored `.env`. The registered callback is `http://localhost:4001/api/auth/login/google/callback`. Rebuild a changed app/service with `docker compose up --build -d web` (or `api` / `auth`).

For Facebook, set its app ID/secret and exact registered HTTPS `FACEBOOK_CALLBACK_URL` in `.env`. A development tunnel can receive that callback while the web app stays on localhost. Update both Meta and `.env` when its address changes.

App code lives in `apps/client` and `apps/ios`; services live in `services`; shared Kotlin models live in `packages/shared`. iOS builds run in Xcode on the Mac.

The private engine is optional and excluded from default startup. Owners can initialize `services/engine-private`, set its service token, and explicitly enable the `engine` profile.
