import type { DashboardSummary } from '../types/insights'

interface DashboardSummaryCardsProps {
  dashboard: DashboardSummary | null
  isLoading: boolean
}

const EMPTY_DASHBOARD: DashboardSummary = {
  totalTasks: 0,
  overdueTasks: 0,
  todayTasks: 0,
  upcomingTasks: 0,
  completedToday: 0,
  completedLast7Days: 0,
}

export function DashboardSummaryCards({ dashboard, isLoading }: DashboardSummaryCardsProps) {
  const data = dashboard ?? EMPTY_DASHBOARD

  return (
    <section className="dashboard-section">
      <div className="section-header">
        <div>
          <h2>대시보드</h2>
          <p>지금 해야 할 일과 최근 완료 흐름을 빠르게 확인합니다.</p>
        </div>
      </div>

      <div className="dashboard-grid">
        <article className="summary-card">
          <span className="summary-card__label">전체 Task</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.totalTasks}</strong>
        </article>
        <article className="summary-card summary-card--overdue">
          <span className="summary-card__label">기한 초과</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.overdueTasks}</strong>
        </article>
        <article className="summary-card summary-card--today">
          <span className="summary-card__label">오늘 할 일</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.todayTasks}</strong>
        </article>
        <article className="summary-card summary-card--upcoming">
          <span className="summary-card__label">예정된 작업</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.upcomingTasks}</strong>
        </article>
        <article className="summary-card">
          <span className="summary-card__label">오늘 완료</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.completedToday}</strong>
        </article>
        <article className="summary-card">
          <span className="summary-card__label">최근 7일 완료</span>
          <strong className="summary-card__value">{isLoading ? '-' : data.completedLast7Days}</strong>
        </article>
      </div>
    </section>
  )
}
