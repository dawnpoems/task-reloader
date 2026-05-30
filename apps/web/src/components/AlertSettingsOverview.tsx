import { formatAlertSendTime } from '../hooks/useAlertSettings'
import type {
  TaskDueEmailAlertLastDelivery,
  TaskDueEmailAlertRecipient,
  TaskDueEmailAlertSettings,
} from '../types/taskDueEmailAlert'

interface AlertSettingsOverviewProps {
  settings: TaskDueEmailAlertSettings
  recipients: TaskDueEmailAlertRecipient[]
  notice: string | null
  lastFailedDelivery: TaskDueEmailAlertLastDelivery | null
  isEditDisabled: boolean
  onEdit: () => void
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

export function AlertSettingsOverview({
  settings,
  recipients,
  notice,
  lastFailedDelivery,
  isEditDisabled,
  onEdit,
}: AlertSettingsOverviewProps) {
  return (
    <div className="alert-settings-panels">
      <section className="alert-settings-panel alert-settings-panel--overview">
        <div className="alert-settings-panel__header">
          <div>
            <h3>현재 알림 설정</h3>
            <p>발송 설정과 수신 이메일을 한눈에 확인합니다.</p>
          </div>
          <button
            type="button"
            onClick={onEdit}
            disabled={isEditDisabled}
          >
            변경
          </button>
        </div>

        {notice && (
          <p className="alert-settings-notice" role="status" aria-live="polite">
            {notice}
          </p>
        )}

        {lastFailedDelivery && (
          <div className="alert-settings-delivery-failure" role="alert">
            <div>
              <span>최근 발송 실패</span>
              <strong>{formatLocalDate(lastFailedDelivery.localDate)} 알림을 보내지 못했습니다.</strong>
            </div>
            <p>
              마지막 시도: {formatCreatedAt(lastFailedDelivery.updatedAt)}
              {' · '}
              {lastFailedDelivery.attemptCount}회 시도
              {' · '}
              수신자 {lastFailedDelivery.recipientCount}명
            </p>
            {lastFailedDelivery.errorMessage && (
              <p className="alert-settings-delivery-failure__reason">
                실패 사유: {lastFailedDelivery.errorMessage}
              </p>
            )}
          </div>
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
              <strong>{formatAlertSendTime(settings.sendTime)}</strong>
              <p>{settings.timezone} 기준</p>
            </div>
            <div className="alert-settings-summary__item">
              <span>최근 발송일</span>
              <strong>{formatLocalDate(settings.lastSentLocalDate)}</strong>
              <p>{lastFailedDelivery ? '실패한 발송은 다시 예약하면 재시도됩니다.' : '성공한 발송일 기준입니다.'}</p>
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
  )
}
