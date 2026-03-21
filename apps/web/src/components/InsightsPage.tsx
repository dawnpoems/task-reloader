import { ErrorNotice } from './ErrorNotice'
import { DashboardSummaryCards } from './DashboardSummaryCards'
import { RecentCompletionsSection } from './RecentCompletionsSection'
import type { DashboardSummary, RecentTaskCompletion } from '../types/insights'

interface InsightsPageProps {
  dashboard: DashboardSummary | null
  recentCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  onOpenTask: (taskId: number) => void
  onRetry: () => void
}

export function InsightsPage({
  dashboard,
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
      <RecentCompletionsSection
        recentCompletions={recentCompletions}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
    </>
  )
}
