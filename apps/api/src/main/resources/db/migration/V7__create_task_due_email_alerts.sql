CREATE TABLE IF NOT EXISTS task_due_email_alert_settings (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  send_time TIME NOT NULL DEFAULT TIME '09:00:00',
  timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Seoul',
  last_sent_local_date DATE NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tdea_settings_timezone_not_blank CHECK (char_length(btrim(timezone)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_tdea_settings_enabled_send_time
  ON task_due_email_alert_settings (enabled, send_time);

CREATE INDEX IF NOT EXISTS idx_tdea_settings_timezone
  ON task_due_email_alert_settings (timezone);

INSERT INTO task_due_email_alert_settings (user_id)
SELECT u.id
FROM users u
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS task_due_email_alert_recipients (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  email VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tdea_recipients_email_not_blank CHECK (char_length(btrim(email)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tdea_recipients_user_lower_email
  ON task_due_email_alert_recipients (user_id, lower(email));

CREATE INDEX IF NOT EXISTS idx_tdea_recipients_user_id
  ON task_due_email_alert_recipients (user_id);

CREATE TABLE IF NOT EXISTS task_due_email_alert_delivery_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  local_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
  attempt_count INTEGER NOT NULL DEFAULT 1,
  recipient_count INTEGER NOT NULL DEFAULT 0,
  error_message TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tdea_logs_attempt_positive CHECK (attempt_count > 0),
  CONSTRAINT chk_tdea_logs_recipient_non_negative CHECK (recipient_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tdea_logs_user_local_date
  ON task_due_email_alert_delivery_logs (user_id, local_date);

CREATE INDEX IF NOT EXISTS idx_tdea_logs_local_date_status
  ON task_due_email_alert_delivery_logs (local_date, status);
