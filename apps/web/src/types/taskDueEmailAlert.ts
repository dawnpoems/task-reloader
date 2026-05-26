export interface TaskDueEmailAlertSettings {
  enabled: boolean
  sendTime: string
  timezone: string
  lastSentLocalDate: string | null
  suggestedEmail: string
  maxRecipientCount: number
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
