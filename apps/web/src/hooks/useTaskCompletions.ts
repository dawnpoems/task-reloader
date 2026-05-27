import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../api/client'
import { tasksApi } from '../api/tasks'
import type { TaskCompletion } from '../types/taskCompletion'

interface UseTaskCompletionsOptions {
  year?: number
  month?: number
  refreshToken?: number
  errorMessage?: string
}

export function useTaskCompletions(
  taskId: number,
  {
    year,
    month,
    refreshToken = 0,
    errorMessage = '완료 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  }: UseTaskCompletionsOptions = {}
) {
  const [completions, setCompletions] = useState<TaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const requestIdRef = useRef(0)

  const refetch = useCallback(async () => {
    const requestId = requestIdRef.current + 1
    requestIdRef.current = requestId
    setIsLoading(true)
    setError(null)

    const query = year !== undefined && month !== undefined ? { year, month } : undefined
    const res = await tasksApi.getCompletions(taskId, query)
    if (requestId !== requestIdRef.current) return

    if (res.success && res.data) {
      setCompletions(res.data)
    } else {
      setCompletions([])
      setError(extractErrorMessage(res.error, errorMessage))
    }
    setIsLoading(false)
  }, [errorMessage, month, taskId, year])

  useEffect(() => {
    refetch()
  }, [refetch, refreshToken])

  return { completions, isLoading, error, refetch }
}
