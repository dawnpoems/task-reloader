ALTER TABLE task_due_email_alert_settings
  ADD COLUMN IF NOT EXISTS next_send_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_tdea_settings_enabled_next_send_at
  ON task_due_email_alert_settings (enabled, next_send_at);

UPDATE task_due_email_alert_settings
SET next_send_at = CASE
  WHEN ((NOW() AT TIME ZONE timezone)::date + send_time) > (NOW() AT TIME ZONE timezone)
    THEN ((NOW() AT TIME ZONE timezone)::date + send_time) AT TIME ZONE timezone
  ELSE ((NOW() AT TIME ZONE timezone)::date + send_time + INTERVAL '1 day') AT TIME ZONE timezone
END
WHERE enabled = TRUE
  AND next_send_at IS NULL;
