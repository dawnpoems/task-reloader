import { useCallback, useEffect, useState } from 'react'
import { tasksApi } from '../api/tasks'
import type { DashboardSummary } from '../types/insights'

interface UseDashboardSummaryReturn {
  dashboard: DashboardSummary | null
  isLoading: boolean
  error: string | null
  refetch: () => Promise<void>
}

export function useDashboardSummary(enabled = true): UseDashboardSummaryReturn {
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null)
  const [isLoading, setIsLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  const fetchDashboard = useCallback(async () => {
    if (!enabled) {
      setDashboard(null)
      setError(null)
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)
    const res = await tasksApi.getDashboard()
    if (res.success) {
      setDashboard(res.data)
    } else {
      setDashboard(null)
      setError('대시보드 요약을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }
    setIsLoading(false)
  }, [enabled])

  useEffect(() => {
    fetchDashboard()
  }, [fetchDashboard])

  return { dashboard, isLoading, error, refetch: fetchDashboard }
}
