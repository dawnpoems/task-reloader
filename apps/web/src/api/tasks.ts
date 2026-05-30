import { apiClient, ApiResponse } from './client'
import { withQuery } from './query'
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
    apiClient.get<Task[]>(withQuery('/tasks', { status: filter })),

  getById: (id: number): Promise<ApiResponse<Task>> =>
    apiClient.get<Task>(`/tasks/${id}`),

  getCompletions: (id: number, query?: CompletionsQuery): Promise<ApiResponse<TaskCompletion[]>> => {
    const hasYear = query?.year !== undefined
    const hasMonth = query?.month !== undefined

    return apiClient.get<TaskCompletion[]>(
      withQuery(`/tasks/${id}/completions`, {
        year: hasYear && hasMonth ? query?.year : undefined,
        month: hasYear && hasMonth ? query?.month : undefined,
      })
    )
  },

  getDashboard: (): Promise<ApiResponse<DashboardSummary>> =>
    apiClient.get<DashboardSummary>('/insights/dashboard'),

  getOverview: (query: InsightsOverviewQuery = {}): Promise<ApiResponse<InsightsOverview>> => {
    const days = query.days ?? 30
    const top = query.top ?? 5
    return apiClient.get<InsightsOverview>(withQuery('/insights/overview', { days, top }))
  },

  getRecentCompletions: (): Promise<ApiResponse<RecentTaskCompletion[]>> =>
    apiClient.get<RecentTaskCompletion[]>('/insights/recent-completions'),

  getTodayCompletions: (): Promise<ApiResponse<RecentTaskCompletion[]>> =>
    apiClient.get<RecentTaskCompletion[]>('/insights/today-completions'),

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
