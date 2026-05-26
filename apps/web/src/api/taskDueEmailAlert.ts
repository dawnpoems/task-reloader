import { apiClient, type ApiResponse } from './client'
import type {
  AddTaskDueEmailAlertRecipientRequest,
  TaskDueEmailAlertRecipient,
  TaskDueEmailAlertSettings,
  UpdateTaskDueEmailAlertSettingsRequest,
} from '../types/taskDueEmailAlert'

const TASK_DUE_EMAIL_ALERT_BASE_PATH = '/alerts/task-due-email'

export const taskDueEmailAlertApi = {
  getSettings: (): Promise<ApiResponse<TaskDueEmailAlertSettings>> =>
    apiClient.get<TaskDueEmailAlertSettings>(`${TASK_DUE_EMAIL_ALERT_BASE_PATH}/settings`),

  updateSettings: (
    request: UpdateTaskDueEmailAlertSettingsRequest
  ): Promise<ApiResponse<TaskDueEmailAlertSettings>> =>
    apiClient.patch<TaskDueEmailAlertSettings>(`${TASK_DUE_EMAIL_ALERT_BASE_PATH}/settings`, request),

  getRecipients: (): Promise<ApiResponse<TaskDueEmailAlertRecipient[]>> =>
    apiClient.get<TaskDueEmailAlertRecipient[]>(`${TASK_DUE_EMAIL_ALERT_BASE_PATH}/recipients`),

  addRecipient: (
    request: AddTaskDueEmailAlertRecipientRequest
  ): Promise<ApiResponse<TaskDueEmailAlertRecipient>> =>
    apiClient.post<TaskDueEmailAlertRecipient>(`${TASK_DUE_EMAIL_ALERT_BASE_PATH}/recipients`, request),

  deleteRecipient: (id: number): Promise<ApiResponse<void>> =>
    apiClient.delete<void>(`${TASK_DUE_EMAIL_ALERT_BASE_PATH}/recipients/${id}`),
}
