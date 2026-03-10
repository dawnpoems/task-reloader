export type TaskStatus = 'PENDING' | 'TODAY' | 'UPCOMING' | 'OVERDUE' | 'COMPLETED'

// 백엔드 TaskResponse 필드명과 동일하게 유지
export interface Task {
  id: number
  name: string
  everyNDays: number
  timezone: string
  status: TaskStatus
  nextDueAt?: string
  completedAt?: string
  lastCompletedAt?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateTaskRequest {
  name: string
  everyNDays: number
  timezone?: string
}

export interface UpdateTaskRequest {
  name?: string
  everyNDays?: number
  timezone?: string
}

// tasksApi.getAll() 쿼리 파라미터용
export type TaskStatusFilter = 'ALL' | 'OVERDUE' | 'TODAY' | 'UPCOMING'

// types/index.ts 에서 사용하던 TaskListResponse 제거 (백엔드는 List<TaskResponse> 직접 반환)
export type TaskListResponse = Task[]


