ALTER TABLE plotus.projects ADD COLUMN IF NOT EXISTS last_opened_section text NOT NULL DEFAULT 'PLOT'
    CHECK (last_opened_section IN ('PLOT', 'FLOOR_PLAN'));
