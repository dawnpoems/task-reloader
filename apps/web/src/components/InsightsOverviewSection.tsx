import { formatDate, formatDateTime } from '../lib/utils'
import type { InsightsOverview, TaskTrendInsight } from '../types/insights'

interface InsightsOverviewSectionProps {
  overview: InsightsOverview | null
  isLoading: boolean
  onOpenTask: (taskId: number) => void
}

const EMPTY_OVERVIEW: InsightsOverview = {
  periodDays: 30,
  periodStart: '',
  periodEnd: '',
  timezone: 'Asia/Seoul',
  activeTaskCount: 0,
  completedTaskCount: 0,
  completionCount: 0,
  delayedCompletionCount: 0,
  completionRatePct: 0,
  delayRatePct: 0,
  averageDelayDays: 0,
  riskyTaskCount: 0,
  riskyTasks: [],
  taskTrends: [],
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`
}

function formatDelayDays(days: number): string {
  if (days <= 0) return '0.00일'
  return `${days.toFixed(2)}일`
}

function toRiskReasonLabel(reason: string): string {
  if (reason === 'OVERDUE_7D_PLUS') return '7일 이상 지연'
  if (reason === 'NO_COMPLETION_30D') return '30일 무완료'
  return reason
}

function sortByCompletion(a: TaskTrendInsight, b: TaskTrendInsight): number {
  return (
    b.completionCount - a.completionCount
    || b.delayedCount - a.delayedCount
    || a.taskId - b.taskId
  )
}

function sortByDelayed(a: TaskTrendInsight, b: TaskTrendInsight): number {
  return (
    b.delayedCount - a.delayedCount
    || b.completionCount - a.completionCount
    || a.taskId - b.taskId
  )
}

function sortByDelayRate(a: TaskTrendInsight, b: TaskTrendInsight): number {
  return (
    b.delayRatePct - a.delayRatePct
    || b.delayedCount - a.delayedCount
    || b.completionCount - a.completionCount
    || a.taskId - b.taskId
  )
}

export function InsightsOverviewSection({
  overview,
  isLoading,
  onOpenTask,
}: InsightsOverviewSectionProps) {
  const data = overview ?? EMPTY_OVERVIEW
  const riskyTasks = data.riskyTasks ?? []
  const taskTrends = data.taskTrends ?? []
  const topByCompletion = [...taskTrends].sort(sortByCompletion).slice(0, 5)
  const topByDelayed = [...taskTrends].sort(sortByDelayed).slice(0, 5)
  const topByDelayRate = [...taskTrends]
    .filter((trend) => trend.completionCount > 0)
    .sort(sortByDelayRate)
    .slice(0, 5)

  return (
    <section className="insights-section">
      <div className="section-header">
        <div>
          <h2>인사이트 요약</h2>
          <p>
            최근 {isLoading ? '-' : data.periodDays}일 성과를 기준으로 완료/지연 패턴을 확인합니다.
          </p>
        </div>
      </div>

      <div className="insights-overview-grid">
        <article className="summary-card summary-card--today">
          <span className="summary-card__label">완료율</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : formatPercent(data.completionRatePct)}
          </strong>
          <small className="summary-card__meta">
            {isLoading ? '-' : `${data.completedTaskCount}/${data.activeTaskCount} Task`}
          </small>
        </article>

        <article className="summary-card summary-card--overdue">
          <span className="summary-card__label">지연율</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : formatPercent(data.delayRatePct)}
          </strong>
          <small className="summary-card__meta">
            {isLoading ? '-' : `${data.delayedCompletionCount}/${data.completionCount} 완료`}
          </small>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">평균 지연날짜</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : formatDelayDays(data.averageDelayDays)}
          </strong>
          <small className="summary-card__meta">지연된 완료만 기준</small>
        </article>
      </div>

      <div className="insights-overview-footnote">
        {isLoading ? (
          <span>집계 기간 계산 중...</span>
        ) : (
          <span>
            집계 기간: {formatDate(data.periodStart)} ~ {formatDate(data.periodEnd)} ({data.timezone}) · 리스크 작업 {data.riskyTaskCount}개
          </span>
        )}
      </div>

      <div className="insights-risky">
        <h3>리스크 작업</h3>
        {isLoading ? (
          <p className="section-state">불러오는 중...</p>
        ) : riskyTasks.length === 0 ? (
          <p className="section-state">현재 리스크 작업이 없습니다.</p>
        ) : (
          <ul className="insights-risky-list">
            {riskyTasks.map((task) => (
              <li key={task.taskId} className="insights-risky-item">
                <div className="insights-risky-item__title">
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => onOpenTask(task.taskId)}
                  >
                    {task.taskName}
                  </button>
                  <div className="insights-risky-item__reasons">
                    {task.reasons.map((reason) => (
                      <span key={reason} className="insights-risky-item__reason-chip">
                        {toRiskReasonLabel(reason)}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="insights-risky-item__meta">
                  <span>다음 예정 {formatDateTime(task.nextDueAt)}</span>
                  <span>마지막 완료 {formatDateTime(task.lastCompletedAt ?? undefined)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="insights-trend">
        <h3>작업별 추세 Top 5</h3>
        {isLoading ? (
          <p className="section-state">불러오는 중...</p>
        ) : taskTrends.length === 0 ? (
          <p className="section-state">해당 기간 완료 이력이 없습니다.</p>
        ) : (
          <div className="insights-trend-grid">
            <article className="insights-trend-card">
              <h4>완료 건수</h4>
              <ol className="insights-trend-ranking">
                {topByCompletion.map((trend, index) => (
                  <li key={`completion-${trend.taskId}`}>
                    <button
                      type="button"
                      className="link-button insight-rank__task"
                      onClick={() => onOpenTask(trend.taskId)}
                    >
                      {index + 1}. {trend.taskName}
                    </button>
                    <span className="insight-rank__value">{trend.completionCount}회</span>
                  </li>
                ))}
              </ol>
            </article>

            <article className="insights-trend-card">
              <h4>지연 건수</h4>
              <ol className="insights-trend-ranking">
                {topByDelayed.map((trend, index) => (
                  <li key={`delayed-${trend.taskId}`}>
                    <button
                      type="button"
                      className="link-button insight-rank__task"
                      onClick={() => onOpenTask(trend.taskId)}
                    >
                      {index + 1}. {trend.taskName}
                    </button>
                    <span className="insight-rank__value">{trend.delayedCount}회</span>
                  </li>
                ))}
              </ol>
            </article>

            <article className="insights-trend-card">
              <h4>지연률</h4>
              <ol className="insights-trend-ranking">
                {topByDelayRate.map((trend, index) => (
                  <li key={`delay-rate-${trend.taskId}`}>
                    <button
                      type="button"
                      className="link-button insight-rank__task"
                      onClick={() => onOpenTask(trend.taskId)}
                    >
                      {index + 1}. {trend.taskName}
                    </button>
                    <span className="insight-rank__value">{formatPercent(trend.delayRatePct)}</span>
                  </li>
                ))}
              </ol>
            </article>
          </div>
        )}
      </div>
    </section>
  )
}
