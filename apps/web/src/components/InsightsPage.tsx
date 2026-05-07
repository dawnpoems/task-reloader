import { ErrorNotice } from './ErrorNotice'
import { DashboardSummaryCards } from './DashboardSummaryCards'
import { InsightsOverviewSection } from './InsightsOverviewSection'
import { RecentCompletionsSection } from './RecentCompletionsSection'
import type { DashboardSummary, InsightsOverview, RecentTaskCompletion } from '../types/insights'

interface InsightsPageProps {
  dashboard: DashboardSummary | null
  overview: InsightsOverview | null
  recentCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  onOpenTask: (taskId: number) => void
  onRetry: () => void
}

export function InsightsPage({
  dashboard,
  overview,
  recentCompletions,
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
      <DashboardSummaryCards dashboard={dashboard} isLoading={isLoading} />
      <InsightsOverviewSection
        overview={overview}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
      <RecentCompletionsSection
        recentCompletions={recentCompletions}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
    </>
  )
}
