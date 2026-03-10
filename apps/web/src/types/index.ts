export interface Task {
  id: string
  title: string
  description: string
  status: 'pending' | 'completed' | 'in-progress'
  createdAt: Date
  updatedAt: Date
  dueDate?: Date
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
  message?: string
}

