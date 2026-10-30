ALTER TABLE plotus.plan_generations DROP CONSTRAINT IF EXISTS plan_generations_status_check;
ALTER TABLE plotus.plan_generations ADD CONSTRAINT plan_generations_status_check
    CHECK (status IN ('queued','running','ready','blocked','failed','cancelling','cancelled'));
