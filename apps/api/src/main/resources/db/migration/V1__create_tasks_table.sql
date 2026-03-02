CREATE TABLE IF NOT EXISTS tasks (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR NOT NULL,
  every_n_days INT NOT NULL CHECK (every_n_days >= 1),
  timezone VARCHAR DEFAULT 'Asia/Seoul',
  next_due_at TIMESTAMPTZ NOT NULL,
  last_completed_at TIMESTAMPTZ NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_active_next_due_at
  ON tasks (is_active, next_due_at);

