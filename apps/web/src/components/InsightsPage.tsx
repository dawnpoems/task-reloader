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
        <div className="app-error" role="alert" aria-live="assertive">
          <p>{error}</p>
          <button type="button" className="btn-secondary" onClick={onRetry}>
            다시 시도
          </button>
        </div>
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
