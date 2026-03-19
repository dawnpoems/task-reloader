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

export function useInsights(): UseInsightsReturn {
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null)
  const [recentCompletions, setRecentCompletions] = useState<RecentTaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchInsights = useCallback(async () => {
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
      setError('대시보드를 불러오지 못했습니다.')
    }

    if (recentRes.success && recentRes.data) {
      setRecentCompletions(recentRes.data)
    } else {
      setRecentCompletions([])
      setError((prev) => prev ?? '최근 완료 작업을 불러오지 못했습니다.')
    }

    setIsLoading(false)
  }, [])

  useEffect(() => {
    fetchInsights()
  }, [fetchInsights])

  return { dashboard, recentCompletions, isLoading, error, refetch: fetchInsights }
}
