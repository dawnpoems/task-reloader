import { useState, useEffect, useCallback, useRef } from 'react'
import { tasksApi } from '../api/tasks'
import { extractErrorMessage } from '../api/client'
import type { Task, CreateTaskRequest, UpdateTaskRequest, TaskStatusFilter } from '../types/task'

interface UseTasksReturn {
  tasks: Task[]
  isLoading: boolean
  error: string | null
  toast: string | null
  refetch: () => Promise<void>
  createTask: (request: CreateTaskRequest) => Promise<boolean>
  updateTask: (id: number, request: UpdateTaskRequest) => Promise<boolean>
  deleteTask: (id: number) => Promise<boolean>
  completeTask: (id: number) => Promise<boolean>
}

export function useTasks(filter: TaskStatusFilter = 'ALL', enabled = true): UseTasksReturn {
  const [tasks, setTasks] = useState<Task[]>([])
  const [isLoading, setIsLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const errorTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const setErrorWithTimeout = useCallback((msg: string) => {
    if (errorTimer.current) clearTimeout(errorTimer.current)
    setError(msg)
    errorTimer.current = setTimeout(() => setError(null), 3000)
  }, [])

  const setToastWithTimeout = useCallback((msg: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current)
    setToast(msg)
    toastTimer.current = setTimeout(() => setToast(null), 2500)
  }, [])

  const fetchTasks = useCallback(async () => {
    if (!enabled) {
      setTasks([])
      setError(null)
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)
    const res = await tasksApi.getAll(filter)
    if (res.success && res.data) {
      setTasks(res.data)
    } else {
      setError('Task를 불러오지 못했습니다. 페이지를 새로고침하거나 잠시 후 다시 시도해 주세요.')
    }
    setIsLoading(false)
  }, [enabled, filter])

  useEffect(() => { fetchTasks() }, [fetchTasks])

  const createTask = async (request: CreateTaskRequest): Promise<boolean> => {
    if (!enabled) {
      setErrorWithTimeout('로그인이 필요합니다.')
      return false
    }

    const res = await tasksApi.create(request)
    if (res.success) { setToastWithTimeout('Task가 추가됐습니다 ✓'); return true }
    setErrorWithTimeout(extractErrorMessage(res.error, 'Task 생성에 실패했습니다.'))
    return false
  }

  const updateTask = async (id: number, request: UpdateTaskRequest): Promise<boolean> => {
    if (!enabled) {
      setErrorWithTimeout('로그인이 필요합니다.')
      return false
    }

    const res = await tasksApi.update(id, request)
    if (res.success) { setToastWithTimeout('Task가 수정됐습니다 ✓'); return true }
    setErrorWithTimeout(extractErrorMessage(res.error, 'Task 수정에 실패했습니다.'))
    return false
  }

  const deleteTask = async (id: number): Promise<boolean> => {
    if (!enabled) {
      setErrorWithTimeout('로그인이 필요합니다.')
      return false
    }

    const res = await tasksApi.delete(id)
    if (res.success) { setTasks((prev) => prev.filter((t) => t.id !== id)); setToastWithTimeout('Task가 삭제됐습니다'); return true }
    setErrorWithTimeout(extractErrorMessage(res.error, 'Task 삭제에 실패했습니다.'))
    return false
  }

  const completeTask = async (id: number): Promise<boolean> => {
    if (!enabled) {
      setErrorWithTimeout('로그인이 필요합니다.')
      return false
    }

    const res = await tasksApi.complete(id)
    if (res.success) { setToastWithTimeout('완료 처리됐습니다 ✓'); return true }
    setErrorWithTimeout(extractErrorMessage(res.error, 'Task 완료 처리에 실패했습니다.'))
    return false
  }

  return { tasks, isLoading, error, toast, refetch: fetchTasks, createTask, updateTask, deleteTask, completeTask }
}
