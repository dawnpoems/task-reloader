import { useAlertSettings } from '../hooks/useAlertSettings'
import { AlertSettingsEditModal } from './AlertSettingsEditModal'
import { AlertSettingsOverview } from './AlertSettingsOverview'
import { ErrorNotice } from './ErrorNotice'

export function AlertSettingsPage() {
  const {
    settings,
    recipients,
    form,
    setForm,
    recipientEmail,
    setRecipientEmail,
    isLoading,
    isSaving,
    isEditingSettings,
    isAddingRecipient,
    deletingRecipientId,
    error,
    actionError,
    notice,
    recipientError,
    recipientNotice,
    loadAlertSettings,
    isFormDirty,
    isRecipientLimitReached,
    isRecipientDuplicate,
    isAlertMutationInProgress,
    recipientInlineHint,
    settingsInlineHint,
    lastFailedDelivery,
    handleSubmitSettings,
    handleStartEditSettings,
    handleCancelEditSettings,
    handleSubmitRecipient,
    handleDeleteRecipient,
    clearActionError,
    clearRecipientError,
  } = useAlertSettings()

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
          <AlertSettingsOverview
            settings={settings}
            recipients={recipients}
            notice={notice}
            lastFailedDelivery={lastFailedDelivery}
            isEditDisabled={isEditingSettings || isAlertMutationInProgress}
            onEdit={handleStartEditSettings}
          />

          {isEditingSettings && (
            <AlertSettingsEditModal
              settings={settings}
              recipients={recipients}
              form={form}
              setForm={setForm}
              recipientEmail={recipientEmail}
              setRecipientEmail={setRecipientEmail}
              isSaving={isSaving}
              isAddingRecipient={isAddingRecipient}
              deletingRecipientId={deletingRecipientId}
              isFormDirty={isFormDirty}
              isRecipientLimitReached={isRecipientLimitReached}
              isRecipientDuplicate={isRecipientDuplicate}
              isAlertMutationInProgress={isAlertMutationInProgress}
              actionError={actionError}
              recipientError={recipientError}
              recipientNotice={recipientNotice}
              recipientInlineHint={recipientInlineHint}
              settingsInlineHint={settingsInlineHint}
              onSubmitSettings={handleSubmitSettings}
              onSubmitRecipient={handleSubmitRecipient}
              onDeleteRecipient={handleDeleteRecipient}
              onCancel={handleCancelEditSettings}
              onClearActionError={clearActionError}
              onClearRecipientError={clearRecipientError}
            />
          )}
        </>
      ) : (
        <p className="alert-settings-empty">알림 설정 정보를 찾지 못했습니다.</p>
      )}
    </section>
  )
}
