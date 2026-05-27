import { useCallback, useEffect, useRef, useState } from 'react'
import { tasksApi } from '../api/tasks'
import type { Task } from '../types/task'

export function useTaskDetail(taskId: number, refreshToken = 0) {
  const [task, setTask] = useState<Task | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const requestIdRef = useRef(0)

  const refetch = useCallback(async () => {
    const requestId = requestIdRef.current + 1
    requestIdRef.current = requestId
    setIsLoading(true)
    setError(null)

    const taskRes = await tasksApi.getById(taskId)
    if (requestId !== requestIdRef.current) return

    if (taskRes.success && taskRes.data) {
      setTask(taskRes.data)
    } else {
      setTask(null)
      setError('Task 상세 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }
    setIsLoading(false)
  }, [taskId])

  useEffect(() => {
    refetch()
  }, [refetch, refreshToken])

  return { task, isLoading, error, refetch }
}
