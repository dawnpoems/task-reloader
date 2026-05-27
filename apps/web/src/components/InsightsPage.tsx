import { ErrorNotice } from './ErrorNotice'
import { InsightsOverviewSection } from './InsightsOverviewSection'
import { TodayDoneSection } from './TodayDoneSection'
import type { DashboardSummary, InsightsOverview, RecentTaskCompletion } from '../types/insights'

interface InsightsPageProps {
  dashboard: DashboardSummary | null
  overview: InsightsOverview | null
  todayCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  onOpenTask: (taskId: number) => void
  onRetry: () => void
}

export function InsightsPage({
  dashboard,
  overview,
  todayCompletions,
  isLoading,
  error,
  onOpenTask,
  onRetry,
}: InsightsPageProps) {
  return (
    <>
      {error && (
        <ErrorNotice message={error} onRetry={onRetry} />
      )}
      <TodayDoneSection
        dashboard={dashboard}
        todayCompletions={todayCompletions}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
      <InsightsOverviewSection
        overview={overview}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
    </>
  )
}
