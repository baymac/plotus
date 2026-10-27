CREATE SCHEMA IF NOT EXISTS plotus;
CREATE TABLE IF NOT EXISTS plotus.projects (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    location varchar(200) NOT NULL,
    client_name varchar(120),
    client_email varchar(254),
    client_phone varchar(40),
    area_sq_ft integer,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS projects_owner_edited ON plotus.projects(owner_id, updated_at DESC, id);
