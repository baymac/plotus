CREATE TABLE IF NOT EXISTS plotus.buildable_areas (
    project_id uuid NOT NULL REFERENCES plotus.projects(id) ON DELETE CASCADE,
    owner_id uuid NOT NULL,
    fingerprint text NOT NULL,
    area jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (project_id,fingerprint)
);
