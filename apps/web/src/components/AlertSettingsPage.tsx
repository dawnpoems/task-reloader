import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { taskDueEmailAlertApi } from '../api/taskDueEmailAlert'
import { extractErrorMessage } from '../api/client'
import type { TaskDueEmailAlertRecipient, TaskDueEmailAlertSettings } from '../types/taskDueEmailAlert'
import { ErrorNotice } from './ErrorNotice'

const TIMEZONE_OPTIONS = [
  'Asia/Seoul',
  'Asia/Tokyo',
  'Asia/Singapore',
  'UTC',
  'America/New_York',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Australia/Sydney',
] as const

interface AlertSettingsForm {
  enabled: boolean
  sendTime: string
  timezone: string
}

function formatSendTime(sendTime: string): string {
  const [hour, minute] = sendTime.split(':')
  if (!hour || !minute) return sendTime
  return `${hour}:${minute}`
}

function createFormFromSettings(settings: TaskDueEmailAlertSettings): AlertSettingsForm {
  return {
    enabled: settings.enabled,
    sendTime: formatSendTime(settings.sendTime),
    timezone: settings.timezone,
  }
}

function formatLocalDate(value: string | null): string {
  if (!value) return '아직 발송 전'

  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

function formatCreatedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function AlertSettingsPage() {
  const [settings, setSettings] = useState<TaskDueEmailAlertSettings | null>(null)
  const [recipients, setRecipients] = useState<TaskDueEmailAlertRecipient[]>([])
  const [form, setForm] = useState<AlertSettingsForm | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const loadAlertSettings = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    setActionError(null)

    const [settingsRes, recipientsRes] = await Promise.all([
      taskDueEmailAlertApi.getSettings(),
      taskDueEmailAlertApi.getRecipients(),
    ])

    if (!settingsRes.success || !settingsRes.data) {
      setError(extractErrorMessage(settingsRes.error, '알림 설정을 불러오지 못했습니다.'))
      setIsLoading(false)
      return
    }

    if (!recipientsRes.success || !recipientsRes.data) {
      setError(extractErrorMessage(recipientsRes.error, '수신 이메일 목록을 불러오지 못했습니다.'))
      setIsLoading(false)
      return
    }

    setSettings(settingsRes.data)
    setForm(createFormFromSettings(settingsRes.data))
    setRecipients(recipientsRes.data)
    setIsLoading(false)
  }, [])

  useEffect(() => {
    loadAlertSettings()
  }, [loadAlertSettings])

  const isFormDirty = useMemo(() => {
    if (!settings || !form) return false
    return (
      form.enabled !== settings.enabled ||
      form.sendTime !== formatSendTime(settings.sendTime) ||
      form.timezone.trim() !== settings.timezone
    )
  }, [form, settings])

  const handleSubmitSettings = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!form || isSaving) return

    const timezone = form.timezone.trim()
    if (!timezone) {
      setActionError('타임존은 필수입니다.')
      return
    }

    setIsSaving(true)
    setActionError(null)
    setNotice(null)

    const res = await taskDueEmailAlertApi.updateSettings({
      enabled: form.enabled,
      sendTime: form.sendTime,
      timezone,
    })

    if (res.success && res.data) {
      setSettings(res.data)
      setForm(createFormFromSettings(res.data))
      setNotice('알림 발송 설정을 저장했습니다.')
    } else {
      setActionError(extractErrorMessage(res.error, '알림 발송 설정 저장에 실패했습니다.'))
    }

    setIsSaving(false)
  }

  const handleResetSettings = () => {
    if (!settings || isSaving) return
    setForm(createFormFromSettings(settings))
    setActionError(null)
  }

  return (
    <section className="alert-settings-page">
      <div className="alert-settings-page__hero">
        <div>
          <p className="alert-settings-page__eyebrow">Email reminder</p>
          <h2>작업 마감 이메일 알림</h2>
          <p>
            매일 설정한 시간에 오늘 마감인 작업과 지난 작업을 모아서 이메일로 받을 수 있습니다.
          </p>
        </div>
        {settings && (
          <span className={`alert-settings-page__status ${settings.enabled ? 'alert-settings-page__status--on' : ''}`}>
            {settings.enabled ? '사용 중' : '꺼짐'}
          </span>
        )}
      </div>

      {isLoading ? (
        <p className="app-loading">알림 설정을 불러오는 중...</p>
      ) : error ? (
        <ErrorNotice message={error} onRetry={loadAlertSettings} />
      ) : settings ? (
        <>
          <div className="alert-settings-grid" aria-label="작업 마감 이메일 알림 현재 설정">
            <article className="alert-settings-card">
              <span className="alert-settings-card__label">발송 상태</span>
              <strong>{settings.enabled ? '매일 발송' : '발송 꺼짐'}</strong>
              <p>{settings.enabled ? '설정한 시간에 자동으로 확인합니다.' : '현재 자동 이메일은 발송되지 않습니다.'}</p>
            </article>
            <article className="alert-settings-card">
              <span className="alert-settings-card__label">발송 시간</span>
              <strong>{formatSendTime(settings.sendTime)}</strong>
              <p>{settings.timezone} 기준</p>
            </article>
            <article className="alert-settings-card">
              <span className="alert-settings-card__label">최근 발송일</span>
              <strong>{formatLocalDate(settings.lastSentLocalDate)}</strong>
              <p>하루에 한 번만 발송됩니다.</p>
            </article>
          </div>

          <div className="alert-settings-panels">
            <section className="alert-settings-panel">
              <div className="alert-settings-panel__header">
                <div>
                  <h3>발송 설정</h3>
                  <p>매일 한 번, 사용자가 설정한 시간과 타임존 기준으로 확인합니다.</p>
                </div>
              </div>

              {notice && (
                <p className="alert-settings-notice" role="status" aria-live="polite">
                  {notice}
                </p>
              )}
              {actionError && (
                <p className="alert-settings-action-error" role="alert">
                  {actionError}
                </p>
              )}

              {form && (
                <form className="alert-settings-form" onSubmit={handleSubmitSettings}>
                  <label className="alert-settings-toggle">
                    <input
                      type="checkbox"
                      checked={form.enabled}
                      onChange={(e) => setForm((prev) => prev ? { ...prev, enabled: e.target.checked } : prev)}
                      disabled={isSaving}
                    />
                    <span className="alert-settings-toggle__track" aria-hidden="true">
                      <span className="alert-settings-toggle__thumb" />
                    </span>
                    <span>
                      <strong>작업 마감 이메일 알림 사용</strong>
                      <small>{form.enabled ? '설정한 시간에 자동 발송됩니다.' : '알림이 꺼져 있어 이메일을 보내지 않습니다.'}</small>
                    </span>
                  </label>

                  <div className="alert-settings-form__grid">
                    <label className="alert-settings-form__field">
                      <span>발송 시간</span>
                      <input
                        type="time"
                        value={form.sendTime}
                        onChange={(e) => setForm((prev) => prev ? { ...prev, sendTime: e.target.value } : prev)}
                        disabled={isSaving}
                        required
                      />
                    </label>
                    <label className="alert-settings-form__field">
                      <span>타임존</span>
                      <input
                        type="text"
                        list="task-due-email-alert-timezones"
                        value={form.timezone}
                        onChange={(e) => setForm((prev) => prev ? { ...prev, timezone: e.target.value } : prev)}
                        disabled={isSaving}
                        required
                      />
                    </label>
                    <datalist id="task-due-email-alert-timezones">
                      {TIMEZONE_OPTIONS.map((timezone) => (
                        <option key={timezone} value={timezone} />
                      ))}
                    </datalist>
                  </div>

                  <div className="alert-settings-form__actions">
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={handleResetSettings}
                      disabled={isSaving || !isFormDirty}
                    >
                      되돌리기
                    </button>
                    <button type="submit" disabled={isSaving || !isFormDirty || !form.timezone.trim()}>
                      {isSaving ? '저장 중...' : '발송 설정 저장'}
                    </button>
                  </div>
                </form>
              )}
            </section>

            <section className="alert-settings-panel">
              <div className="alert-settings-panel__header">
                <div>
                  <h3>수신 이메일</h3>
                  <p>
                    {recipients.length}/{settings.maxRecipientCount}개 등록됨
                  </p>
                </div>
                <span className="alert-settings-panel__hint">
                  추천: {settings.suggestedEmail}
                </span>
              </div>

              {recipients.length === 0 ? (
                <p className="alert-settings-empty">
                  아직 등록된 수신 이메일이 없습니다. 다음 단계에서 이메일 추가/삭제를 연결할 예정입니다.
                </p>
              ) : (
                <ul className="alert-settings-recipient-list">
                  {recipients.map((recipient) => (
                    <li key={recipient.id} className="alert-settings-recipient-list__item">
                      <strong>{recipient.email}</strong>
                      <span>{formatCreatedAt(recipient.createdAt)} 등록</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      ) : (
        <p className="alert-settings-empty">알림 설정 정보를 찾지 못했습니다.</p>
      )}
    </section>
  )
}
