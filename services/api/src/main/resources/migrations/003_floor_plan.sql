ALTER TABLE plotus.projects ADD COLUMN IF NOT EXISTS floor_plan jsonb;
CREATE TABLE IF NOT EXISTS plotus.plan_generations (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    project_id uuid NOT NULL REFERENCES plotus.projects(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    source_revision bigint NOT NULL,
    snapshot jsonb NOT NULL,
    status text NOT NULL CHECK (status IN ('queued','running','ready','blocked','failed')),
    result jsonb,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (owner_id, project_id, request_id)
);
CREATE INDEX IF NOT EXISTS plan_generations_project ON plotus.plan_generations(owner_id,project_id,created_at DESC);
