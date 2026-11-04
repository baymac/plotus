-- A private, frozen example, installed by the operator from a checked generation.
-- No client names, addresses or contact details are included in this template.
CREATE TABLE IF NOT EXISTS plotus.welcome_templates (
    template_key text PRIMARY KEY,
    brief jsonb NOT NULL,
    generation jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK (generation->>'status' = 'ready'),
    CHECK (COALESCE(generation->>'draft','false') = 'false')
);

-- Keep this record after a demo is deleted, so it never comes back on refresh.
CREATE TABLE IF NOT EXISTS plotus.project_onboarding (
    owner_id uuid PRIMARY KEY,
    template_key text,
    project_id uuid,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Existing workspaces retain their current projects.
INSERT INTO plotus.project_onboarding(owner_id)
SELECT DISTINCT owner_id FROM plotus.projects
ON CONFLICT (owner_id) DO NOTHING;
