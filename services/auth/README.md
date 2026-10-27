# Plotus auth

A separate Go authentication service, freshly written for Plotus and kept in this public repository. Google, Facebook and passkeys are the supported sign-in methods. It owns its own PostgreSQL schema, Redis state and RSA signing key. It does not call the private Naksha/Kickapp auth service or share its accounts, sessions or keys. App UI stays Kotlin Multiplatform; the domain API stays Kotlin/Ktor.

## Local setup

```sh
cd services/auth
./scripts/init-local.sh
cd ../../docker-local
docker compose up --build -d
curl http://localhost:4001/health
curl http://localhost:4001/api/auth/config
```

The script creates ignored `docker-local/.env` and `services/auth/.local/signing-key.pem` files, preserving existing ones. Loopback ports: auth 4001, PostgreSQL 5441, Redis 6381. These containers and volumes are separate from the existing private product. Migrations run before startup. Local Compose uses the invoking user's UID/GID to read the protected key; provision the key for a non-root service account in deployment.

Google is disabled until this service's client ID and secret are set in the ignored `docker-local/.env`. Register the exact callback `http://localhost:4001/api/auth/login/google/callback` locally, or `${AUTH_PUBLIC_URL}/api/auth/login/google/callback` in deployment. Never put the secret in the apps. Deployment requires HTTPS, a fixed web origin, `AUTH_ENV=production`. Browser and auth origins must be on the same site for refresh cookies. Configure proxy rate limits separately; this service deliberately ignores forwarded-IP headers.

## Browser flow

1. `POST /api/auth/login/google` with `{"redirect":"/"}` from the configured origin returns `authUrl`. Navigate the browser there.
2. The callback verifies Google's signature/issuer/audience, verified email, nonce, OAuth state, browser binding and provider PKCE. It creates an account/session, sets an HttpOnly refresh cookie and returns to the configured web origin.
3. Fetch `/api/auth/token/csrf`, then `/api/auth/token/access` with `X-CSRF-Token`. Use `credentials: include` for cross-origin browser requests. Keep the 15-minute access token in memory.
4. `POST /api/auth/token/refresh` rotates the hashed refresh credential with CSRF protection. `DELETE /api/auth/login/user/logout` revokes the session and clears cookies.

Cookie-backed endpoints require an allowed Origin or same-origin Fetch Metadata. Refresh sessions expire absolutely after seven days. Concurrent rotation uses compare-and-swap; an old refresh credential cannot rotate twice. APIs must verify RS256, `iss=plotus-auth`, `aud=plotus-api`, expiration and key ID against `/.well-known/jwks.json`. Auth's `/user/me` additionally checks session revocation in storage.

## iOS and desktop flow

Generate a random verifier/state and use the OS authentication browser:

```text
GET /api/auth/native/google/start
    ?redirect_uri=plotus://oauth/callback
    &state=<random state>
    &code_challenge=<base64url SHA256(verifier)>
    &code_challenge_method=S256
```

OS-browser clients can use `plotus://oauth/callback`. Desktop may bind an ephemeral loopback port and use `http://127.0.0.1:<port>/callback`. Redirects match `AUTH_NATIVE_REDIRECTS`; dynamic ports are allowed only for the explicitly configured literal loopback host/path. No wildcard redirects.

The callback contains a single-use code and app state, never tokens. Verify state, then `POST /api/auth/native/token/exchange` with `code`, `code_verifier`, `redirect_uri`. The grant expires in 90 seconds and is bound to the proof and exact redirect. Store native refresh/CSRF credentials in the platform secure store (Keychain on iOS), access tokens in memory. `POST /api/auth/native/token/refresh` and `DELETE /api/auth/native/session` both take JSON `refresh_token` and `csrf_token`.

The shared app restores browser sessions through HttpOnly cookies and keeps access tokens in memory. iOS uses the Google Sign-In SDK with the web client as its server audience, a server-issued nonce and a single-use PKCE proof. Its refresh credential stays in Keychain.

The SDK calls `POST /api/auth/native/google/challenge` with an S256 challenge, then `/native/google/token` with the returned attempt, Google ID token and verifier. The service checks Google's signature, audience, expiry, nonce and authorized party before issuing a Plotus session. The OS adapter lives in `apps/ios`; app UI and session state remain shared Kotlin in `apps/client`.

Copy `apps/ios/Config.local.xcconfig.example` to ignored `apps/ios/Config.local.xcconfig` for the iOS client IDs and HTTPS service address. Client secrets stay on the server. Native passkeys need a public RP domain; localhost browser passkeys work independently.

## Facebook sign-in

Set `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` and `FACEBOOK_CALLBACK_URL` in the ignored local environment. The callback must be registered as an exact HTTPS URL in the Meta app. Google retains its own callback. `FACEBOOK_GRAPH_VERSION` defaults to `v26.0`.

Web starts with `POST /api/auth/login/facebook`; iPhone uses `POST /api/auth/native/facebook`. Both supply random `state`, an S256 `code_challenge` and `code_challenge_method`. Web also supplies a relative `redirect`; native supplies its allowlisted `redirect_uri`. Open the returned `authUrl` in the browser (the iPhone uses `ASWebAuthenticationSession`). The server binds the authorization to a secure callback-origin cookie, verifies state, exchanges the code, validates the app/user/expiry with Meta, and fetches the profile using an app-secret proof.

Web returns a 90-second single-use code in the URL fragment. After validating state, exchange it with the original verifier at `POST /api/auth/login/facebook/exchange` to receive the normal HttpOnly browser session. Native returns a code and state to `plotus://oauth/callback` and uses the existing native token exchange. Meta access tokens and app secrets never go to the app.

Facebook email is optional. Accounts are keyed by their provider identity; matching email addresses do not automatically link Google and Facebook accounts. If a temporary tunnel changes, update the registered Meta callback and local `FACEBOOK_CALLBACK_URL`, then restart auth. Cloud Compose derives the callback from its production domain.

## Passkeys

- `POST /api/auth/passkey/login/begin` returns a standard WebAuthn `publicKey` envelope. `/finish` takes the platform assertion JSON. Keep the same cookie jar. User verification is required.
- Native equivalents: `/api/auth/native/passkey/login/begin` and `/finish`. Retain the challenge cookie between calls. iOS needs a real RP domain and the matching associated-domain entitlement before device use; localhost is only a browser development configuration.
- Enrollment requires an authenticated bearer: `/api/auth/passkey/register/begin` and `/finish`. Credentials must be discoverable and user-verified. Google or Facebook bootstraps the initial account; unknown credentials do not create accounts.
- List/delete owned credentials: `GET /api/auth/passkey/credentials`, `DELETE /api/auth/passkey/credentials/{id}`. Verified assertions update stored counters/backup state; clone warnings reject login.
- Configure `WEBAUTHN_RP_ID` and the exact comma-separated `WEBAUTHN_ORIGINS`. Challenges expire after five minutes and are single-use and cookie-bound.

## Check

With Go 1.27.1:

```sh
go test ./...
go vet ./...
docker compose -f ../../docker-local/docker-compose.yml --env-file ../../docker-local/.env up -d postgres redis
./scripts/check-integration.sh
```

No development bypass, fixed token or mock-user route exists. JSON errors contain `error.code` and `error.message`. Storage failures return 503. Health requires the schema and Redis. Keys, refresh credentials, OAuth codes and request bodies are not logged.
