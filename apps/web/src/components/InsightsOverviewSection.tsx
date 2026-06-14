import { useState } from 'react'
import { formatDate, formatDateTime } from '../lib/utils'
import type { InsightsOverview } from '../types/insights'

interface InsightsOverviewSectionProps {
  overview: InsightsOverview | null
  isLoading: boolean
  onOpenTask: (taskId: number) => void
  onEditTask: (taskId: number) => void
  onDeleteTask: (taskId: number) => Promise<boolean>
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
  topCompletionTrends: [],
  topDelayedTrends: [],
  topDelayRateTrends: [],
  taskTrends: [],
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`
}

function toRiskReasonLabel(reason: string): string {
  if (reason === 'OVERDUE_7D_PLUS') return '7일 이상 지연'
  if (reason === 'NO_COMPLETION_30D') return '30일 무완료'
  return reason
}

function toRiskReasonDescription(reason: string): string {
  if (reason === 'OVERDUE_7D_PLUS') return '다음 예정일이 7일 넘게 지나서 일정 재조정이 필요합니다.'
  if (reason === 'NO_COMPLETION_30D') return '최근 30일 동안 완료 이력이 없어 작업 유지 여부를 확인해야 합니다.'
  return '작업 상태를 확인해야 합니다.'
}

export function InsightsOverviewSection({
  overview,
  isLoading,
  onOpenTask,
  onEditTask,
  onDeleteTask,
}: InsightsOverviewSectionProps) {
  const [deletingTaskIds, setDeletingTaskIds] = useState<Set<number>>(new Set())
  const data = overview ?? EMPTY_OVERVIEW
  const riskyTasks = data.riskyTasks ?? []
  const overdueRiskCount = riskyTasks.filter((task) => task.reasons.includes('OVERDUE_7D_PLUS')).length
  const staleRiskCount = riskyTasks.filter((task) => task.reasons.includes('NO_COMPLETION_30D')).length

  const handleDelete = async (taskId: number, taskName: string) => {
    const confirmed = window.confirm(`"${taskName}" Task를 삭제할까요?`)
    if (!confirmed) return

    setDeletingTaskIds((prev) => {
      const next = new Set(prev)
      next.add(taskId)
      return next
    })

    try {
      await onDeleteTask(taskId)
    } finally {
      setDeletingTaskIds((prev) => {
        const next = new Set(prev)
        next.delete(taskId)
        return next
      })
    }
  }

  return (
    <section className="insights-section">
      <div className="section-header">
        <div>
          <h2>확인할 신호</h2>
          <p>
            전체 목록에서 놓치기 쉬운 방치, 지연, 무완료 작업만 골라봅니다.
          </p>
        </div>
      </div>

      <div className="insights-overview-grid">
        <article className="summary-card summary-card--overdue">
          <span className="summary-card__label">리스크 작업</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : data.riskyTaskCount}
          </strong>
          <small className="summary-card__meta">
            전체 활성 작업 {isLoading ? '-' : data.activeTaskCount}개 중
          </small>
        </article>

        <article className="summary-card summary-card--overdue">
          <span className="summary-card__label">오래 지연</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : overdueRiskCount}
          </strong>
          <small className="summary-card__meta">
            7일 이상 지난 작업
          </small>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">무완료 신호</span>
          <strong className="summary-card__value">
            {isLoading ? '-' : staleRiskCount}
          </strong>
          <small className="summary-card__meta">30일간 완료 이력 없음</small>
        </article>
      </div>

      <div className="insights-overview-footnote">
        {isLoading ? (
          <span>집계 기간 계산 중...</span>
        ) : (
          <span>
            기준 기간: {formatDate(data.periodStart)} ~ {formatDate(data.periodEnd)} ({data.timezone}) · 최근 {data.periodDays}일 완료율 {formatPercent(data.completionRatePct)}
          </span>
        )}
      </div>

      <div className="insights-risky">
        <h3>개입이 필요한 작업</h3>
        {isLoading ? (
          <p className="section-state">불러오는 중...</p>
        ) : riskyTasks.length === 0 ? (
          <p className="section-state">현재 리스크 작업이 없습니다.</p>
        ) : (
          <ul className="insights-risky-list">
            {riskyTasks.map((task) => {
              const canManageStaleTask = task.reasons.includes('NO_COMPLETION_30D')
              const isDeleting = deletingTaskIds.has(task.taskId)

              return (
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
                    <span>다음 예정 {formatDate(task.nextDueAt)}</span>
                    <span>마지막 완료 {formatDateTime(task.lastCompletedAt ?? undefined)}</span>
                  </div>
                  <p className="insights-risky-item__hint">
                    {toRiskReasonDescription(task.reasons[0])}
                  </p>
                  {canManageStaleTask && (
                    <div className="insights-risky-item__actions">
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => onEditTask(task.taskId)}
                        disabled={isDeleting}
                      >
                        변경
                      </button>
                      <button
                        type="button"
                        className="btn-delete"
                        onClick={() => handleDelete(task.taskId, task.taskName)}
                        disabled={isDeleting}
                      >
                        {isDeleting ? '삭제 중...' : '삭제'}
                      </button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </section>
  )
}
