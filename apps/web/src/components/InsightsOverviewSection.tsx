import { formatDate } from '../lib/utils'
import type { InsightsOverview } from '../types/insights'

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
  averageDelayMinutes: 0,
  riskyTaskCount: 0,
  taskTrends: [],
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`
}

function formatDelayMinutes(minutes: number): string {
  if (minutes <= 0) return '0분'
  const hours = Math.floor(minutes / 60)
  const remains = Math.round(minutes % 60)
  if (hours === 0) return `${Math.round(minutes)}분`
  if (remains === 0) return `${hours}시간`
  return `${hours}시간 ${remains}분`
}

export function InsightsOverviewSection({
  overview,
  isLoading,
  onOpenTask,
}: InsightsOverviewSectionProps) {
  const data = overview ?? EMPTY_OVERVIEW

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
          <span className="summary-card__label">평균 지연 시간</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : formatDelayMinutes(data.averageDelayMinutes)}
          </strong>
          <small className="summary-card__meta">지연된 완료만 기준</small>
        </article>

        <article className="summary-card summary-card--upcoming">
          <span className="summary-card__label">리스크 작업</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : data.riskyTaskCount}
          </strong>
          <small className="summary-card__meta">7일+ overdue 또는 30일 무완료</small>
        </article>
      </div>

      <div className="insights-overview-footnote">
        {isLoading ? (
          <span>집계 기간 계산 중...</span>
        ) : (
          <span>
            집계 기간: {formatDate(data.periodStart)} ~ {formatDate(data.periodEnd)} ({data.timezone})
          </span>
        )}
      </div>

      <div className="insights-trend">
        <h3>작업별 추세 Top {isLoading ? '-' : data.taskTrends.length}</h3>
        {isLoading ? (
          <p className="section-state">불러오는 중...</p>
        ) : data.taskTrends.length === 0 ? (
          <p className="section-state">해당 기간 완료 이력이 없습니다.</p>
        ) : (
          <ol className="insights-trend-list">
            {data.taskTrends.map((trend) => (
              <li key={trend.taskId} className="insight-trend-item">
                <div className="insight-trend-item__title">
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => onOpenTask(trend.taskId)}
                  >
                    {trend.taskName}
                  </button>
                  <span className="insight-trend-item__rate">{formatPercent(trend.delayRatePct)}</span>
                </div>
                <div className="insight-trend-item__meta">
                  <span>완료 {trend.completionCount}회</span>
                  <span>지연 {trend.delayedCount}회</span>
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
