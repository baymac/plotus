-- Keep existing Google users and sessions intact; identities are provider-scoped.
ALTER TABLE plotus_auth.users ALTER COLUMN google_subject DROP NOT NULL;
ALTER TABLE plotus_auth.users ADD COLUMN IF NOT EXISTS facebook_subject text UNIQUE;
