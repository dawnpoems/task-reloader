import { formatDateTime } from '../lib/utils'
import type { RecentTaskCompletion } from '../types/insights'

interface RecentCompletionsSectionProps {
  recentCompletions: RecentTaskCompletion[]
  isLoading: boolean
  onOpenTask: (taskId: number) => void
}

export function RecentCompletionsSection({
  recentCompletions,
  isLoading,
  onOpenTask,
}: RecentCompletionsSectionProps) {
  return (
    <section className="insights-section">
      <div className="section-header">
        <div>
          <h2>최근 완료한 작업</h2>
          <p>완료 시점 기준으로 다음 일정이 어떻게 밀렸는지 바로 확인할 수 있습니다.</p>
        </div>
      </div>

      {isLoading ? (
        <p className="section-state">불러오는 중...</p>
      ) : recentCompletions.length === 0 ? (
        <p className="section-state">아직 완료한 작업이 없습니다.</p>
      ) : (
        <ul className="recent-completions">
          {recentCompletions.map((completion) => (
            <li key={completion.id} className="recent-completion-card">
              <div className="recent-completion-card__top">
                <button
                  type="button"
                  className="link-button"
                  onClick={() => onOpenTask(completion.taskId)}
                >
                  {completion.taskName}
                </button>
                <span>{formatDateTime(completion.completedAt)}</span>
              </div>
              <div className="recent-completion-card__meta">
                <span>이전 예정 {formatDateTime(completion.previousDueAt)}</span>
                <span>다음 예정 {formatDateTime(completion.nextDueAt)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
