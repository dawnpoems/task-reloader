import { useCallback, useEffect, useState } from 'react'
import { tasksApi } from '../api/tasks'
import type { DashboardSummary, InsightsOverview, RecentTaskCompletion } from '../types/insights'

interface UseInsightsReturn {
  dashboard: DashboardSummary | null
  overview: InsightsOverview | null
  todayCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  refetch: () => Promise<void>
}

export function useInsights(enabled = true): UseInsightsReturn {
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null)
  const [overview, setOverview] = useState<InsightsOverview | null>(null)
  const [todayCompletions, setTodayCompletions] = useState<RecentTaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  const fetchInsights = useCallback(async () => {
    if (!enabled) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)

    const [dashboardRes, overviewRes, todayRes] = await Promise.all([
      tasksApi.getDashboard(),
      tasksApi.getOverview({ days: 30, top: 5 }),
      tasksApi.getTodayCompletions(),
    ])

    if (dashboardRes.success && dashboardRes.data) {
      setDashboard(dashboardRes.data)
    } else {
      setDashboard(null)
      setError('인사이트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    if (overviewRes.success && overviewRes.data) {
      setOverview(overviewRes.data)
    } else {
      setOverview(null)
      setError((prev) => prev ?? '인사이트 요약을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }

    if (todayRes.success && todayRes.data) {
      setTodayCompletions(todayRes.data)
    } else {
      setTodayCompletions([])
      setError((prev) => prev ?? '오늘 완료 작업을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
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

  return { dashboard, overview, todayCompletions, isLoading, error, refetch: fetchInsights }
}
