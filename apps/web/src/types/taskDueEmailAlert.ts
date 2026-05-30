export interface TaskDueEmailAlertSettings {
  enabled: boolean
  sendTime: string
  timezone: string
  lastSentLocalDate: string | null
  suggestedEmail: string
  maxRecipientCount: number
  lastDelivery: TaskDueEmailAlertLastDelivery | null
}

export type TaskDueEmailAlertDeliveryStatus = 'SENT' | 'FAILED' | 'SKIPPED'

export interface TaskDueEmailAlertLastDelivery {
  status: TaskDueEmailAlertDeliveryStatus
  localDate: string
  attemptCount: number
  recipientCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface UpdateTaskDueEmailAlertSettingsRequest {
  enabled?: boolean
  sendTime?: string
  timezone?: string
}

export interface TaskDueEmailAlertRecipient {
  id: number
  email: string
  createdAt: string
}

export interface AddTaskDueEmailAlertRecipientRequest {
  email: string
}
