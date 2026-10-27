CREATE SCHEMA IF NOT EXISTS plotus_auth;
CREATE TABLE IF NOT EXISTS plotus_auth.users (
    id uuid PRIMARY KEY,
    google_subject text UNIQUE NOT NULL,
    email text NOT NULL,
    name text NOT NULL,
    picture text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS plotus_auth.sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES plotus_auth.users(id),
    kind text NOT NULL CHECK (kind IN ('web','native')),
    refresh_hash text UNIQUE NOT NULL,
    csrf_token text NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sessions_user ON plotus_auth.sessions(user_id);
CREATE TABLE IF NOT EXISTS plotus_auth.passkeys (
 credential_id text PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES plotus_auth.users(id),
 label text NOT NULL DEFAULT '',
 credential jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 last_used_at timestamptz
);
