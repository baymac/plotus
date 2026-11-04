CREATE TABLE IF NOT EXISTS plotus.plan_reviews (
    generation_id uuid PRIMARY KEY REFERENCES plotus.plan_generations(id) ON DELETE CASCADE,
    owner_id uuid NOT NULL,
    project_id uuid NOT NULL REFERENCES plotus.projects(id) ON DELETE CASCADE,
    source_hash text NOT NULL,
    report jsonb NOT NULL,
    ai_review jsonb,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS plotus.plan_optimizations (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    project_id uuid NOT NULL REFERENCES plotus.projects(id) ON DELETE CASCADE,
    base_generation_id uuid NOT NULL REFERENCES plotus.plan_generations(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    source_revision bigint NOT NULL,
    snapshot jsonb NOT NULL,
    operations jsonb NOT NULL,
    status text NOT NULL CHECK (status IN ('queued','running','cancelling','ready','no_improvement','cancelled','failed','applied','dismissed')),
    result jsonb NOT NULL,
    applied_plan_id uuid REFERENCES plotus.plan_generations(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(owner_id,project_id,request_id)
);
CREATE INDEX IF NOT EXISTS plan_optimizations_project ON plotus.plan_optimizations(owner_id,project_id,created_at DESC);
