CREATE TABLE IF NOT EXISTS plotus.account_preferences (
    owner_id uuid PRIMARY KEY,
    units text NOT NULL CHECK (units IN ('FEET_INCHES', 'METRES')),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
