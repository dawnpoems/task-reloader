import { apiClient, ApiResponse } from './client'
import type { Task, CreateTaskRequest, UpdateTaskRequest, TaskStatusFilter } from '../types/task'
import type { DashboardSummary, InsightsOverview, RecentTaskCompletion } from '../types/insights'
import type { TaskCompletion } from '../types/taskCompletion'

interface CompletionsQuery {
  year?: number
  month?: number
}

interface InsightsOverviewQuery {
  days?: number
  top?: number
}

export const tasksApi = {
  getAll: (filter: TaskStatusFilter = 'ALL'): Promise<ApiResponse<Task[]>> =>
    apiClient.get<Task[]>(`/tasks?status=${filter}`),

  getById: (id: number): Promise<ApiResponse<Task>> =>
    apiClient.get<Task>(`/tasks/${id}`),

  getCompletions: (id: number, query?: CompletionsQuery): Promise<ApiResponse<TaskCompletion[]>> => {
    const hasYear = query?.year !== undefined
    const hasMonth = query?.month !== undefined

    if (hasYear && hasMonth) {
      return apiClient.get<TaskCompletion[]>(`/tasks/${id}/completions?year=${query?.year}&month=${query?.month}`)
    }
    return apiClient.get<TaskCompletion[]>(`/tasks/${id}/completions`)
  },

  getDashboard: (): Promise<ApiResponse<DashboardSummary>> =>
    apiClient.get<DashboardSummary>('/insights/dashboard'),

  getOverview: (query: InsightsOverviewQuery = {}): Promise<ApiResponse<InsightsOverview>> => {
    const days = query.days ?? 30
    const top = query.top ?? 5
    return apiClient.get<InsightsOverview>(`/insights/overview?days=${days}&top=${top}`)
  },

  getRecentCompletions: (): Promise<ApiResponse<RecentTaskCompletion[]>> =>
    apiClient.get<RecentTaskCompletion[]>('/insights/recent-completions'),

  create: (request: CreateTaskRequest): Promise<ApiResponse<Task>> =>
    apiClient.post<Task>('/tasks', request),

  // 백엔드는 PATCH
  update: (id: number, request: UpdateTaskRequest): Promise<ApiResponse<Task>> =>
    apiClient.patch<Task>(`/tasks/${id}`, request),

  delete: (id: number): Promise<ApiResponse<void>> =>
    apiClient.delete<void>(`/tasks/${id}`),

  // 백엔드는 POST /{id}/complete
  complete: (id: number): Promise<ApiResponse<Task>> =>
    apiClient.post<Task>(`/tasks/${id}/complete`, {}),
}
