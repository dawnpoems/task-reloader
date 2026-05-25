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

function isSuggestedEmailRegistered(
  settings: TaskDueEmailAlertSettings,
  recipients: TaskDueEmailAlertRecipient[]
): boolean {
  const suggestedEmail = settings.suggestedEmail.trim().toLowerCase()
  if (!suggestedEmail) return true
  return recipients.some((recipient) => recipient.email.toLowerCase() === suggestedEmail)
}

function getDefaultRecipientEmail(
  settings: TaskDueEmailAlertSettings,
  recipients: TaskDueEmailAlertRecipient[]
): string {
  return isSuggestedEmailRegistered(settings, recipients) ? '' : settings.suggestedEmail
}

export function AlertSettingsPage() {
  const [settings, setSettings] = useState<TaskDueEmailAlertSettings | null>(null)
  const [recipients, setRecipients] = useState<TaskDueEmailAlertRecipient[]>([])
  const [form, setForm] = useState<AlertSettingsForm | null>(null)
  const [recipientEmail, setRecipientEmail] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [isEditingSettings, setIsEditingSettings] = useState(false)
  const [isAddingRecipient, setIsAddingRecipient] = useState(false)
  const [deletingRecipientId, setDeletingRecipientId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [recipientError, setRecipientError] = useState<string | null>(null)
  const [recipientNotice, setRecipientNotice] = useState<string | null>(null)

  const loadAlertSettings = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    setActionError(null)
    setRecipientError(null)

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

    const loadedSettings = settingsRes.data
    const loadedRecipients = recipientsRes.data

    setSettings(loadedSettings)
    setForm(createFormFromSettings(loadedSettings))
    setRecipients(loadedRecipients)
    setRecipientEmail((prev) => prev.trim() ? prev : getDefaultRecipientEmail(loadedSettings, loadedRecipients))
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

  const isRecipientLimitReached = settings ? recipients.length >= settings.maxRecipientCount : true
  const normalizedRecipientEmail = recipientEmail.trim().toLowerCase()
  const isRecipientDuplicate = normalizedRecipientEmail
    ? recipients.some((recipient) => recipient.email.toLowerCase() === normalizedRecipientEmail)
    : false

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
      setIsEditingSettings(false)
    } else {
      setActionError(extractErrorMessage(res.error, '알림 발송 설정 저장에 실패했습니다.'))
    }

    setIsSaving(false)
  }

  const handleStartEditSettings = () => {
    if (!settings || isSaving) return
    setForm(createFormFromSettings(settings))
    setRecipientEmail((prev) => prev.trim() ? prev : getDefaultRecipientEmail(settings, recipients))
    setActionError(null)
    setNotice(null)
    setRecipientError(null)
    setRecipientNotice(null)
    setIsEditingSettings(true)
  }

  const handleCancelEditSettings = () => {
    if (!settings || isSaving) return
    setForm(createFormFromSettings(settings))
    setActionError(null)
    setRecipientError(null)
    setRecipientNotice(null)
    setIsEditingSettings(false)
  }

  const handleSubmitRecipient = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!settings || isAddingRecipient || deletingRecipientId !== null) return

    const email = recipientEmail.trim()
    if (!email) {
      setRecipientError('수신 이메일을 입력해 주세요.')
      return
    }

    if (isRecipientLimitReached) {
      setRecipientError(`수신 이메일은 최대 ${settings.maxRecipientCount}개까지 등록할 수 있습니다.`)
      return
    }

    if (isRecipientDuplicate) {
      setRecipientError('이미 등록된 이메일입니다.')
      return
    }

    setIsAddingRecipient(true)
    setRecipientError(null)
    setRecipientNotice(null)

    const res = await taskDueEmailAlertApi.addRecipient({ email })

    if (res.success && res.data) {
      const addedRecipient = res.data
      const nextRecipients = [...recipients, addedRecipient]

      setRecipients(nextRecipients)
      setRecipientEmail(getDefaultRecipientEmail(settings, nextRecipients))
      setRecipientNotice('수신 이메일을 추가했습니다.')
    } else {
      setRecipientError(extractErrorMessage(res.error, '수신 이메일 추가에 실패했습니다.'))
    }

    setIsAddingRecipient(false)
  }

  const handleDeleteRecipient = async (recipient: TaskDueEmailAlertRecipient) => {
    if (!settings || isAddingRecipient || deletingRecipientId !== null) return

    setDeletingRecipientId(recipient.id)
    setRecipientError(null)
    setRecipientNotice(null)

    const res = await taskDueEmailAlertApi.deleteRecipient(recipient.id)

    if (res.success) {
      const nextRecipients = recipients.filter((item) => item.id !== recipient.id)

      setRecipients(nextRecipients)
      setRecipientNotice('수신 이메일을 삭제했습니다.')
      setRecipientEmail((prev) => prev.trim() ? prev : getDefaultRecipientEmail(settings, nextRecipients))
    } else {
      setRecipientError(extractErrorMessage(res.error, '수신 이메일 삭제에 실패했습니다.'))
    }

    setDeletingRecipientId(null)
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
      </div>

      {isLoading ? (
        <p className="app-loading">알림 설정을 불러오는 중...</p>
      ) : error ? (
        <ErrorNotice message={error} onRetry={loadAlertSettings} />
      ) : settings ? (
        <>
          <div className="alert-settings-panels">
            <section className="alert-settings-panel alert-settings-panel--overview">
              <div className="alert-settings-panel__header">
                <div>
                  <h3>현재 알림 설정</h3>
                  <p>발송 설정과 수신 이메일을 한눈에 확인합니다.</p>
                </div>
                <button
                  type="button"
                  onClick={handleStartEditSettings}
                  disabled={isEditingSettings || isSaving || isAddingRecipient || deletingRecipientId !== null}
                >
                  변경
                </button>
              </div>

              {notice && (
                <p className="alert-settings-notice" role="status" aria-live="polite">
                  {notice}
                </p>
              )}

              <div className="alert-settings-summary" aria-label="현재 발송 설정">
                <div className="alert-settings-summary__grid">
                  <div className="alert-settings-summary__item">
                    <span>사용 여부</span>
                    <strong>{settings.enabled ? '사용 중' : '사용 안 함'}</strong>
                    <p>{settings.enabled ? '설정한 시간에 자동 발송됩니다.' : '알림이 꺼져 있어 이메일을 보내지 않습니다.'}</p>
                  </div>
                  <div className="alert-settings-summary__item">
                    <span>발송 시간</span>
                    <strong>{formatSendTime(settings.sendTime)}</strong>
                    <p>{settings.timezone} 기준</p>
                  </div>
                  <div className="alert-settings-summary__item">
                    <span>최근 발송일</span>
                    <strong>{formatLocalDate(settings.lastSentLocalDate)}</strong>
                    <p>하루에 한 번만 발송됩니다.</p>
                  </div>
                </div>

                <div className="alert-settings-overview-recipients">
                  <div className="alert-settings-overview-recipients__header">
                    <div>
                      <h4>수신 이메일</h4>
                      <p>{recipients.length}/{settings.maxRecipientCount}개 등록됨</p>
                    </div>
                    <span className="alert-settings-panel__hint">
                      기본 이메일: {settings.suggestedEmail}
                    </span>
                  </div>
                  {recipients.length === 0 ? (
                    <p className="alert-settings-empty">
                      아직 등록된 수신 이메일이 없습니다.
                    </p>
                  ) : (
                    <ul className="alert-settings-recipient-list alert-settings-recipient-list--readonly">
                      {recipients.map((recipient) => (
                        <li key={recipient.id} className="alert-settings-recipient-list__item">
                          <div>
                            <strong>{recipient.email}</strong>
                            <span>{formatCreatedAt(recipient.createdAt)} 등록</span>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </section>
          </div>

          {isEditingSettings && (
            <div className="modal-backdrop">
              <section
                className="modal alert-settings-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="alert-settings-modal-title"
              >
                <div className="modal__header">
                  <h2 id="alert-settings-modal-title">알림 설정 변경</h2>
                  <button
                    type="button"
                    className="modal__close"
                    aria-label="알림 설정 변경 닫기"
                    onClick={handleCancelEditSettings}
                    disabled={isSaving || isAddingRecipient || deletingRecipientId !== null}
                  >
                    ×
                  </button>
                </div>

                <div className="modal__body">
                  <div className="alert-settings-edit-grid">
                    <section className="alert-settings-edit-section">
                      <h4>발송 설정</h4>
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
                              onClick={handleCancelEditSettings}
                              disabled={isSaving}
                            >
                              닫기
                            </button>
                            <button type="submit" disabled={isSaving || !isFormDirty || !form.timezone.trim()}>
                              {isSaving ? '저장 중...' : '발송 설정 저장'}
                            </button>
                          </div>
                        </form>
                      )}
                    </section>

                    <section className="alert-settings-edit-section">
                      <h4>수신 이메일 변경</h4>
                      {recipientNotice && (
                        <p className="alert-settings-notice" role="status" aria-live="polite">
                          {recipientNotice}
                        </p>
                      )}
                      {recipientError && (
                        <p className="alert-settings-action-error" role="alert">
                          {recipientError}
                        </p>
                      )}

                      <form className="alert-settings-recipient-form" onSubmit={handleSubmitRecipient}>
                        {isRecipientLimitReached && (
                          <p className="alert-settings-recipient-limit-warning" role="alert">
                            수신 이메일은 최대 {settings.maxRecipientCount}개까지만 등록 가능합니다.
                          </p>
                        )}
                        <label className="alert-settings-form__field">
                          <span>이메일 주소</span>
                          <input
                            type="email"
                            value={recipientEmail}
                            onChange={(e) => setRecipientEmail(e.target.value)}
                            placeholder={settings.suggestedEmail}
                            disabled={isAddingRecipient || deletingRecipientId !== null || isRecipientLimitReached}
                            required
                          />
                        </label>
                        <div className="alert-settings-recipient-form__actions">
                          <button
                            type="submit"
                            disabled={
                              isAddingRecipient ||
                              deletingRecipientId !== null ||
                              isRecipientLimitReached ||
                              !recipientEmail.trim() ||
                              isRecipientDuplicate
                            }
                          >
                            {isAddingRecipient ? '추가 중...' : '수신 이메일 추가'}
                          </button>
                        </div>
                      </form>

                      {!isRecipientLimitReached && (
                        <p className="alert-settings-recipient-limit">
                          최대 {settings.maxRecipientCount}개까지 등록할 수 있습니다.
                        </p>
                      )}

                      {recipients.length === 0 ? (
                        <p className="alert-settings-empty">
                          아직 등록된 수신 이메일이 없습니다. 기본 이메일을 그대로 추가하거나 직접 입력해서 추가해 주세요.
                        </p>
                      ) : (
                        <ul className="alert-settings-recipient-list">
                          {recipients.map((recipient) => (
                            <li key={recipient.id} className="alert-settings-recipient-list__item">
                              <div>
                                <strong>{recipient.email}</strong>
                                <span>{formatCreatedAt(recipient.createdAt)} 등록</span>
                              </div>
                              <button
                                type="button"
                                className="btn-secondary alert-settings-recipient-list__delete"
                                onClick={() => handleDeleteRecipient(recipient)}
                                disabled={isAddingRecipient || deletingRecipientId !== null}
                              >
                                {deletingRecipientId === recipient.id ? '삭제 중...' : '삭제'}
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </section>
                  </div>
                </div>
              </section>
            </div>
          )}
        </>
      ) : (
        <p className="alert-settings-empty">알림 설정 정보를 찾지 못했습니다.</p>
      )}
    </section>
  )
}
