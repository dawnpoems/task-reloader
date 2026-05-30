import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { extractErrorMessage } from '../api/client'
import { taskDueEmailAlertApi } from '../api/taskDueEmailAlert'
import type { TaskDueEmailAlertRecipient, TaskDueEmailAlertSettings } from '../types/taskDueEmailAlert'

export interface AlertSettingsForm {
  enabled: boolean
  sendTime: string
  timezone: string
}

export function formatAlertSendTime(sendTime: string): string {
  const [hour, minute] = sendTime.split(':')
  if (!hour || !minute) return sendTime
  return `${hour}:${minute}`
}

function createFormFromSettings(settings: TaskDueEmailAlertSettings): AlertSettingsForm {
  return {
    enabled: settings.enabled,
    sendTime: formatAlertSendTime(settings.sendTime),
    timezone: settings.timezone,
  }
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

export function useAlertSettings() {
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

    if (!settingsRes.success) {
      setError(extractErrorMessage(settingsRes.error, '알림 설정을 불러오지 못했습니다.'))
      setIsLoading(false)
      return
    }

    if (!recipientsRes.success) {
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
      form.sendTime !== formatAlertSendTime(settings.sendTime) ||
      form.timezone.trim() !== settings.timezone
    )
  }, [form, settings])

  const isRecipientLimitReached = settings ? recipients.length >= settings.maxRecipientCount : true
  const normalizedRecipientEmail = recipientEmail.trim().toLowerCase()
  const isRecipientDuplicate = normalizedRecipientEmail
    ? recipients.some((recipient) => recipient.email.toLowerCase() === normalizedRecipientEmail)
    : false
  const isAlertMutationInProgress = isSaving || isAddingRecipient || deletingRecipientId !== null
  const recipientInlineHint = isRecipientDuplicate
    ? '이미 등록된 이메일입니다.'
    : isRecipientLimitReached
      ? `수신 이메일은 최대 ${settings?.maxRecipientCount ?? 5}개까지만 등록 가능합니다.`
      : ''
  const settingsInlineHint = form && !form.timezone.trim()
    ? '타임존은 필수입니다.'
    : form && !isFormDirty
      ? '변경한 내용이 없습니다.'
      : ''
  const lastFailedDelivery = settings?.lastDelivery?.status === 'FAILED' ? settings.lastDelivery : null

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

    if (res.success) {
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

    if (res.success) {
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

  return {
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
    clearActionError: () => setActionError(null),
    clearRecipientError: () => setRecipientError(null),
  }
}
