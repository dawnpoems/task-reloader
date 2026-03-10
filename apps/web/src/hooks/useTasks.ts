import { useState, useEffect, useCallback } from 'react'
import { tasksApi } from '../api/tasks'
import type { Task, CreateTaskRequest, UpdateTaskRequest, TaskStatusFilter } from '../types/task'

interface UseTasksReturn {
  tasks: Task[]
  isLoading: boolean
  error: string | null
  refetch: () => Promise<void>
  createTask: (request: CreateTaskRequest) => Promise<boolean>
  updateTask: (id: number, request: UpdateTaskRequest) => Promise<boolean>
  deleteTask: (id: number) => Promise<boolean>
  completeTask: (id: number) => Promise<boolean>
}

export function useTasks(filter: TaskStatusFilter = 'ALL'): UseTasksReturn {
  const [tasks, setTasks] = useState<Task[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchTasks = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    const res = await tasksApi.getAll(filter)
    if (res.success && res.data) {
      setTasks(res.data)
    } else {
      setError('Task를 불러오지 못했습니다.')
    }
    setIsLoading(false)
  }, [filter])

  useEffect(() => { fetchTasks() }, [fetchTasks])

  const createTask = async (request: CreateTaskRequest): Promise<boolean> => {
    const res = await tasksApi.create(request)
    if (res.success) { await fetchTasks(); return true }
    setError('Task 생성에 실패했습니다.')
    return false
  }

  const updateTask = async (id: number, request: UpdateTaskRequest): Promise<boolean> => {
    const res = await tasksApi.update(id, request)
    if (res.success) { await fetchTasks(); return true }
    setError('Task 수정에 실패했습니다.')
    return false
  }

  const deleteTask = async (id: number): Promise<boolean> => {
    const res = await tasksApi.delete(id)
    if (res.success) { setTasks((prev) => prev.filter((t) => t.id !== id)); return true }
    setError('Task 삭제에 실패했습니다.')
    return false
  }

  const completeTask = async (id: number): Promise<boolean> => {
    const res = await tasksApi.complete(id)
    if (res.success && res.data) {
      setTasks((prev) => prev.map((t) => (t.id === id ? res.data! : t)))
      return true
    }
    setError('Task 완료 처리에 실패했습니다.')
    return false
  }

  return { tasks, isLoading, error, refetch: fetchTasks, createTask, updateTask, deleteTask, completeTask }
}
