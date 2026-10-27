# Cloud deployment

```sh
cd docker-cloud
cp .env.example .env
# Fill in your domain, Google credentials, database password and signing-key path.
docker compose up --build -d
```

Point the domain at the host and make ports 80/443 available. Caddy serves the optimized web app, obtains HTTPS certificates and routes auth/API requests internally. The database, Redis, API and auth service have no published host ports.

Register `https://YOUR_DOMAIN/api/auth/login/google/callback` with the production Google web client. The same domain is used for web passkeys. iPhone passkeys additionally need the app's associated-domain configuration.

The signing key stays on the server and must be readable by the configured auth UID/GID. This environment has its own database and certificate volumes. The private engine is an optional profile, excluded from default deployment.
