import { DashboardSummaryCards } from './DashboardSummaryCards'
import { RecentCompletionsSection } from './RecentCompletionsSection'
import type { DashboardSummary, RecentTaskCompletion } from '../types/insights'

interface InsightsPageProps {
  dashboard: DashboardSummary | null
  recentCompletions: RecentTaskCompletion[]
  isLoading: boolean
  error: string | null
  onOpenTask: (taskId: number) => void
}

export function InsightsPage({
  dashboard,
  recentCompletions,
  isLoading,
  error,
  onOpenTask,
}: InsightsPageProps) {
  return (
    <>
      {error && <p className="app-error">{error}</p>}
      <DashboardSummaryCards dashboard={dashboard} isLoading={isLoading} />
      <RecentCompletionsSection
        recentCompletions={recentCompletions}
        isLoading={isLoading}
        onOpenTask={onOpenTask}
      />
    </>
  )
}
