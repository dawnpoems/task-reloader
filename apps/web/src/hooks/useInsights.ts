import { useCallback, useEffect, useState } from 'react'
import { tasksApi } from '../api/tasks'
import type { DashboardSummary, RecentTaskCompletion } from '../types/insights'

interface UseInsightsReturn {
  dashboard: DashboardSummary | null
  recentCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  refetch: () => Promise<void>
}

export function useInsights(enabled = true): UseInsightsReturn {
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null)
  const [recentCompletions, setRecentCompletions] = useState<RecentTaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  const fetchInsights = useCallback(async () => {
    if (!enabled) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)

    const [dashboardRes, recentRes] = await Promise.all([
      tasksApi.getDashboard(),
      tasksApi.getRecentCompletions(),
    ])

    if (dashboardRes.success && dashboardRes.data) {
      setDashboard(dashboardRes.data)
    } else {
      setDashboard(null)
      setError('인사이트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    if (recentRes.success && recentRes.data) {
      setRecentCompletions(recentRes.data)
    } else {
      setRecentCompletions([])
      setError((prev) => prev ?? '최근 완료 작업을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    setIsLoading(false)
  }, [enabled])

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false)
      return
    }
    fetchInsights()
  }, [enabled, fetchInsights])

  return { dashboard, recentCompletions, isLoading, error, refetch: fetchInsights }
}
