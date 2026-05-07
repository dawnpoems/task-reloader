CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  approved_at TIMESTAMPTZ NULL,
  approved_by BIGINT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_status
  ON users (status);

INSERT INTO users (
  email,
  password_hash,
  role,
  status,
  approved_at,
  created_at,
  updated_at
)
VALUES (
  '${auth_admin_email}',
  '${auth_admin_password_hash}',
  'ADMIN',
  'APPROVED',
  NOW(),
  NOW(),
  NOW()
)
ON CONFLICT (email)
DO UPDATE SET
  role = 'ADMIN',
  status = 'APPROVED',
  approved_at = COALESCE(users.approved_at, NOW()),
  updated_at = NOW();

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id_expires_at
  ON refresh_tokens (user_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
  ON refresh_tokens (expires_at);

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE tasks
SET user_id = admin.id
FROM users admin
WHERE admin.email = '${auth_admin_email}'
  AND tasks.user_id IS NULL;

DO $$
DECLARE
  v_admin_id BIGINT;
BEGIN
  SELECT id INTO v_admin_id
  FROM users
  WHERE email = '${auth_admin_email}'
  LIMIT 1;

  IF v_admin_id IS NULL THEN
    RAISE EXCEPTION 'Admin user not found for email %', '${auth_admin_email}';
  END IF;

  EXECUTE format(
    'ALTER TABLE tasks ALTER COLUMN user_id SET DEFAULT %s',
    v_admin_id
  );
END $$;

ALTER TABLE tasks
  ALTER COLUMN user_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_tasks_user_id'
  ) THEN
    ALTER TABLE tasks
      ADD CONSTRAINT fk_tasks_user_id
      FOREIGN KEY (user_id) REFERENCES users(id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tasks_user_id
  ON tasks (user_id);

CREATE INDEX IF NOT EXISTS idx_tasks_user_active_next_due_at
  ON tasks (user_id, is_active, next_due_at);

ALTER TABLE task_completions ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE task_completions tc
SET user_id = t.user_id
FROM tasks t
WHERE tc.task_id = t.id
  AND tc.user_id IS NULL;

CREATE OR REPLACE FUNCTION set_task_completion_user_id()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.user_id IS NULL THEN
    SELECT t.user_id INTO NEW.user_id
    FROM tasks t
    WHERE t.id = NEW.task_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_set_task_completion_user_id ON task_completions;

CREATE TRIGGER trg_set_task_completion_user_id
BEFORE INSERT ON task_completions
FOR EACH ROW
EXECUTE FUNCTION set_task_completion_user_id();

ALTER TABLE task_completions
  ALTER COLUMN user_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_task_completions_user_id'
  ) THEN
    ALTER TABLE task_completions
      ADD CONSTRAINT fk_task_completions_user_id
      FOREIGN KEY (user_id) REFERENCES users(id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_task_completions_user_completed_at
  ON task_completions (user_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_completions_user_task_completed_at
  ON task_completions (user_id, task_id, completed_at DESC);
