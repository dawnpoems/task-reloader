import { useRef } from 'react'
import { useModalFocusTrap } from '../hooks/useModalFocusTrap'
import type { AlertSettingsForm } from '../hooks/useAlertSettings'
import type { TaskDueEmailAlertRecipient, TaskDueEmailAlertSettings } from '../types/taskDueEmailAlert'

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

interface AlertSettingsEditModalProps {
  settings: TaskDueEmailAlertSettings
  recipients: TaskDueEmailAlertRecipient[]
  form: AlertSettingsForm | null
  setForm: React.Dispatch<React.SetStateAction<AlertSettingsForm | null>>
  recipientEmail: string
  setRecipientEmail: React.Dispatch<React.SetStateAction<string>>
  isSaving: boolean
  isAddingRecipient: boolean
  deletingRecipientId: number | null
  isFormDirty: boolean
  isRecipientLimitReached: boolean
  isRecipientDuplicate: boolean
  isAlertMutationInProgress: boolean
  actionError: string | null
  recipientError: string | null
  recipientNotice: string | null
  recipientInlineHint: string
  settingsInlineHint: string
  onSubmitSettings: (e: React.FormEvent<HTMLFormElement>) => void
  onSubmitRecipient: (e: React.FormEvent<HTMLFormElement>) => void
  onDeleteRecipient: (recipient: TaskDueEmailAlertRecipient) => void
  onCancel: () => void
  onClearActionError: () => void
  onClearRecipientError: () => void
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

export function AlertSettingsEditModal({
  settings,
  recipients,
  form,
  setForm,
  recipientEmail,
  setRecipientEmail,
  isSaving,
  isAddingRecipient,
  deletingRecipientId,
  isFormDirty,
  isRecipientLimitReached,
  isRecipientDuplicate,
  isAlertMutationInProgress,
  actionError,
  recipientError,
  recipientNotice,
  recipientInlineHint,
  settingsInlineHint,
  onSubmitSettings,
  onSubmitRecipient,
  onDeleteRecipient,
  onCancel,
  onClearActionError,
  onClearRecipientError,
}: AlertSettingsEditModalProps) {
  const closeButtonRef = useRef<HTMLButtonElement | null>(null)
  const { modalRef, handleKeyDown } = useModalFocusTrap<HTMLElement>({
    onRequestClose: onCancel,
    isCloseDisabled: isAlertMutationInProgress,
    initialFocusRef: closeButtonRef,
  })

  return (
    <div className="modal-backdrop">
      <section
        ref={modalRef}
        className="modal alert-settings-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="alert-settings-modal-title"
        onKeyDown={handleKeyDown}
      >
        <div className="modal__header">
          <h2 id="alert-settings-modal-title">알림 설정 변경</h2>
          <button
            ref={closeButtonRef}
            type="button"
            className="modal__close"
            aria-label="알림 설정 변경 닫기"
            onClick={onCancel}
            disabled={isAlertMutationInProgress}
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
                <form className="alert-settings-form" onSubmit={onSubmitSettings}>
                  <label className="alert-settings-toggle">
                    <input
                      type="checkbox"
                      checked={form.enabled}
                      onChange={(e) => {
                        onClearActionError()
                        setForm((prev) => prev ? { ...prev, enabled: e.target.checked } : prev)
                      }}
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
                        onChange={(e) => {
                          onClearActionError()
                          setForm((prev) => prev ? { ...prev, sendTime: e.target.value } : prev)
                        }}
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
                        onChange={(e) => {
                          onClearActionError()
                          setForm((prev) => prev ? { ...prev, timezone: e.target.value } : prev)
                        }}
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
                    {settingsInlineHint && (
                      <p className="alert-settings-inline-hint" role="status">
                        {settingsInlineHint}
                      </p>
                    )}
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={onCancel}
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

              <form className="alert-settings-recipient-form" onSubmit={onSubmitRecipient}>
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
                    onChange={(e) => {
                      setRecipientEmail(e.target.value)
                      onClearRecipientError()
                    }}
                    placeholder={settings.suggestedEmail}
                    disabled={isAddingRecipient || deletingRecipientId !== null || isRecipientLimitReached}
                    required
                  />
                </label>
                {recipientInlineHint && (
                  <p
                    className={`alert-settings-inline-hint ${isRecipientDuplicate || isRecipientLimitReached ? 'alert-settings-inline-hint--error' : ''}`}
                    role={isRecipientDuplicate || isRecipientLimitReached ? 'alert' : 'status'}
                  >
                    {recipientInlineHint}
                  </p>
                )}
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
                        onClick={() => onDeleteRecipient(recipient)}
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
  )
}
