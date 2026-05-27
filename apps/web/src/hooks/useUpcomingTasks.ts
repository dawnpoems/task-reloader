import { useCallback, useEffect, useState } from 'react'
import { extractErrorMessage } from '../api/client'
import { tasksApi } from '../api/tasks'
import type { Task } from '../types/task'

export function useUpcomingTasks(enabled: boolean) {
  const [tasks, setTasks] = useState<Task[]>([])
  const [isOpen, setIsOpen] = useState(false)
  const [isLoaded, setIsLoaded] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reset = useCallback(() => {
    setTasks([])
    setIsOpen(false)
    setIsLoaded(false)
    setIsLoading(false)
    setError(null)
  }, [])

  const fetchTasks = useCallback(async () => {
    if (!enabled) return

    setIsLoading(true)
    setError(null)
    const res = await tasksApi.getAll('UPCOMING')
    if (res.success && res.data) {
      setTasks(res.data)
      setIsLoaded(true)
    } else {
      setError(extractErrorMessage(res.error, '남은 일정을 불러오지 못했습니다.'))
    }
    setIsLoading(false)
  }, [enabled])

  useEffect(() => {
    if (!enabled) {
      reset()
    }
  }, [enabled, reset])

  useEffect(() => {
    if (isOpen && !isLoaded && !isLoading) {
      fetchTasks()
    }
  }, [fetchTasks, isLoaded, isLoading, isOpen])

  const toggleOpen = useCallback(() => {
    setIsOpen((prev) => !prev)
  }, [])

  return {
    tasks,
    isOpen,
    isLoaded,
    isLoading,
    error,
    fetchTasks,
    toggleOpen,
  }
}
