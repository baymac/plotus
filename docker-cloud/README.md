# Cloud deployment

Cloudflare Pages serves the landing page, public information pages and Kotlin/Compose web app in one deployment. The app uses `/projects`, `/settings`, `/settings/general` and `/settings/account`. Protected pages verify the backend session; signed-out visitors are redirected to `/login`.

## Backend

```sh
cd docker-cloud
cp .env.example .env
# Set the production credentials, separate database passwords and signing-key path.
docker compose up --build -d
```

The gateway listens only on `127.0.0.1:4003`. Expose it through an HTTPS Cloudflare Tunnel and set `PLOTUS_BACKEND_ORIGIN` in `wrangler.jsonc` to that origin. The current deployment uses the existing named tunnel's `/__plotus_app` route; the gateway removes that transport prefix. Project/auth databases and Redis have private Docker networking and production-only volumes. The signing key stays on the server, readable by the configured auth UID/GID.

Register these provider callbacks:

- Google: `https://plotus.app/api/auth/login/google/callback`
- Facebook: `https://plotus.app/api/auth/login/facebook/callback`

Google's authorised web origin is `https://plotus.app`. Web passkeys use `plotus.app` as their relying party. iPhone passkeys also require associated-domain configuration.

## Website and app

With Node 22+, JDK 17 and authenticated Wrangler available:

```sh
./docker-cloud/deploy-web.sh
```

This builds the landing site and optimised Kotlin app together, then deploys the combined output to the existing `plotus-landing` Pages project. The app's assets live under `/_app/`; the root stays the landing page. Deploy this combined build when updating the website, so the app routes remain available. No repository automation is required.

The optional private engine remains excluded from the default deployment.
