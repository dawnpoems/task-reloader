import { useEffect, useMemo, useState } from 'react'
import { extractErrorMessage } from '../api/client'
import { tasksApi } from '../api/tasks'
import { formatDate, formatDateTime } from '../lib/utils'
import type { Task } from '../types/task'
import type { TaskCompletion } from '../types/taskCompletion'

interface TaskDetailPageProps {
  taskId: number
  refreshToken?: number
  onBack: () => void
  onEdit: (task: Task) => void
  onComplete: (id: number) => Promise<boolean>
}

const toActionableMessage = (prefix: string, reason: string, action = '잠시 후 다시 시도해 주세요.'): string =>
  `${prefix} ${reason} ${action}`

export function TaskDetailPage({ taskId, refreshToken = 0, onBack, onEdit, onComplete }: TaskDetailPageProps) {
  const [task, setTask] = useState<Task | null>(null)
  const [completions, setCompletions] = useState<TaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCompletionsLoading, setIsCompletionsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [completionsError, setCompletionsError] = useState<string | null>(null)
  const [taskRetryToken, setTaskRetryToken] = useState(0)
  const [completionsRetryToken, setCompletionsRetryToken] = useState(0)
  const [isCompleting, setIsCompleting] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [viewMonth, setViewMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [selectedDateKey, setSelectedDateKey] = useState<string | null>(null)

  const toKstDateKey = (dateTime: string): string =>
    new Date(dateTime).toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' })

  const completionsByDate = useMemo(() => {
    const grouped = new Map<string, TaskCompletion[]>()
    for (const completion of completions) {
      const key = toKstDateKey(completion.completedAt)
      const bucket = grouped.get(key)
      if (bucket) {
        bucket.push(completion)
      } else {
        grouped.set(key, [completion])
      }
    }
    return grouped
  }, [completions])

  const monthPrefix = useMemo(
    () => `${viewMonth.getFullYear()}-${String(viewMonth.getMonth() + 1).padStart(2, '0')}`,
    [viewMonth]
  )

  const daysInMonth = useMemo(
    () => new Date(viewMonth.getFullYear(), viewMonth.getMonth() + 1, 0).getDate(),
    [viewMonth]
  )

  const leadingBlanks = useMemo(
    () => new Date(viewMonth.getFullYear(), viewMonth.getMonth(), 1).getDay(),
    [viewMonth]
  )

  const calendarDays = useMemo(
    () => Array.from({ length: daysInMonth }, (_, i) => i + 1),
    [daysInMonth]
  )

  const selectedCompletions = useMemo(
    () => (selectedDateKey ? completionsByDate.get(selectedDateKey) ?? [] : []),
    [completionsByDate, selectedDateKey]
  )

  useEffect(() => {
    let active = true

    const fetchTask = async () => {
      setIsLoading(true)
      setLoadError(null)

      const taskRes = await tasksApi.getById(taskId)

      if (!active) return

      if (taskRes.success && taskRes.data) {
        setTask(taskRes.data)
      } else {
        setTask(null)
        const reason = extractErrorMessage(taskRes.error, '요청 중 오류가 발생했습니다.')
        setLoadError(toActionableMessage('Task 상세 정보를 불러오지 못했습니다.', reason))
      }
      setIsLoading(false)
    }

    fetchTask()
    return () => { active = false }
  }, [taskId, refreshToken, taskRetryToken])

  useEffect(() => {
    let active = true

    const fetchMonthCompletions = async () => {
      setIsCompletionsLoading(true)
      setCompletionsError(null)
      const year = viewMonth.getFullYear()
      const month = viewMonth.getMonth() + 1
      const completionsRes = await tasksApi.getCompletions(taskId, { year, month })

      if (!active) return

      if (completionsRes.success && completionsRes.data) {
        setCompletions(completionsRes.data)
      } else {
        setCompletions([])
        const reason = extractErrorMessage(completionsRes.error, '요청 중 오류가 발생했습니다.')
        setCompletionsError(toActionableMessage('완료 이력을 불러오지 못했습니다.', reason))
      }
      setIsCompletionsLoading(false)
    }

    fetchMonthCompletions()
    return () => { active = false }
  }, [taskId, viewMonth, refreshToken, completionsRetryToken])

  useEffect(() => {
    if (!selectedDateKey) return
    if (!selectedDateKey.startsWith(monthPrefix)) {
      setSelectedDateKey(null)
    }
  }, [monthPrefix, selectedDateKey])

  const handleComplete = async () => {
    if (isCompleting) return

    setActionError(null)
    setIsCompleting(true)
    try {
      const ok = await onComplete(taskId)
      if (!ok) {
        setActionError('완료 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.')
        return
      }

      const year = viewMonth.getFullYear()
      const month = viewMonth.getMonth() + 1
      const [taskRes, completionsRes] = await Promise.all([
        tasksApi.getById(taskId),
        tasksApi.getCompletions(taskId, { year, month }),
      ])

      if (taskRes.success && taskRes.data) setTask(taskRes.data)
      if (completionsRes.success && completionsRes.data) setCompletions(completionsRes.data)
    } finally {
      setIsCompleting(false)
    }
  }

  if (isLoading) {
    return <p className="app-loading">상세 정보를 불러오는 중...</p>
  }

  if (loadError || !task) {
    return (
      <section className="detail-page">
        <button type="button" className="btn-secondary detail-page__back" onClick={onBack}>
          목록으로
        </button>
        <div className="app-error" role="alert" aria-live="assertive">
          <p>{loadError ?? 'Task를 찾을 수 없습니다. 목록으로 돌아간 뒤 다시 열어 주세요.'}</p>
          <button type="button" className="btn-secondary" onClick={() => setTaskRetryToken((prev) => prev + 1)}>
            다시 시도
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="detail-page">
      <div className="detail-page__header">
        <button type="button" className="btn-secondary detail-page__back" onClick={onBack}>
          목록으로
        </button>
        <div className="detail-page__actions">
          {task.isActive && (
            <button
              type="button"
              className="btn-complete detail-page__action-btn detail-page__action-btn--complete"
              onClick={handleComplete}
              disabled={isCompleting}
              aria-disabled={isCompleting}
              title={isCompleting ? '완료 처리 중입니다.' : undefined}
            >
              {isCompleting ? '처리 중...' : '완료'}
            </button>
          )}
          <button
            type="button"
            className="btn-edit detail-page__action-btn detail-page__action-btn--edit"
            onClick={() => onEdit(task)}
            disabled={isCompleting}
            aria-disabled={isCompleting}
            title={isCompleting ? '완료 처리 중에는 수정할 수 없습니다.' : undefined}
          >
            수정
          </button>
        </div>
      </div>

      <article className="detail-hero">
        <span className={`detail-hero__badge detail-hero__badge--${task.status.toLowerCase()}`}>
          {task.status}
        </span>
        <h2>{task.name}</h2>
        <p>완료 시점 기준으로 다음 일정을 계산하는 반복 작업입니다.</p>
      </article>

      {actionError && <p className="app-error" role="alert" aria-live="assertive">{actionError}</p>}

      <div className="detail-grid">
        <section className="detail-card">
          <h3>기본 정보</h3>
          <dl className="detail-card__list">
            <div><dt>반복 주기</dt><dd>매 {task.everyNDays}일</dd></div>
            <div><dt>다음 예정</dt><dd>{formatDateTime(task.nextDueAt)}</dd></div>
            <div><dt>시작일</dt><dd>{task.startDate ?? '-'}</dd></div>
            <div><dt>최근 완료</dt><dd>{formatDateTime(task.completedAt)}</dd></div>
            <div><dt>생성일</dt><dd>{formatDate(task.createdAt)}</dd></div>
          </dl>
        </section>

        <section className="detail-card">
          <div className="detail-card__title-row">
            <h3>완료 달력</h3>
            <div className="detail-calendar__nav">
              <button
                type="button"
                className="btn-secondary detail-calendar__nav-btn"
                onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
              >
                이전
              </button>
              <strong>{viewMonth.getFullYear()}년 {viewMonth.getMonth() + 1}월</strong>
              <button
                type="button"
                className="btn-secondary detail-calendar__nav-btn"
                onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
              >
                다음
              </button>
            </div>
          </div>

          <div className="detail-calendar">
            {['일', '월', '화', '수', '목', '금', '토'].map((weekday) => (
              <span key={weekday} className="detail-calendar__weekday">{weekday}</span>
            ))}

            {Array.from({ length: leadingBlanks }).map((_, idx) => (
              <span key={`blank-${idx}`} className="detail-calendar__blank" />
            ))}

            {calendarDays.map((day) => {
              const dateKey = `${monthPrefix}-${String(day).padStart(2, '0')}`
              const completionCount = completionsByDate.get(dateKey)?.length ?? 0
              const isSelected = selectedDateKey === dateKey

              return (
                <button
                  key={dateKey}
                  type="button"
                  className={`detail-calendar__day ${completionCount > 0 ? 'detail-calendar__day--has-data' : ''} ${isSelected ? 'detail-calendar__day--selected' : ''}`}
                  onClick={() => setSelectedDateKey(dateKey)}
                >
                  <span>{day}</span>
                  {completionCount > 0 && (
                    <span className="detail-calendar__dot">
                      {completionCount > 1 ? completionCount : ''}
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          <div className="detail-calendar__selected">
            {isCompletionsLoading ? (
              <p className="section-state">완료 이력을 불러오는 중...</p>
            ) : completionsError ? (
              <div className="app-error" role="alert" aria-live="assertive">
                <p>{completionsError}</p>
                <button type="button" className="btn-secondary" onClick={() => setCompletionsRetryToken((prev) => prev + 1)}>
                  다시 시도
                </button>
              </div>
            ) : !selectedDateKey ? (
              <p className="section-state">날짜를 선택하면 완료 이력을 확인할 수 있습니다.</p>
            ) : selectedCompletions.length === 0 ? (
              <p className="section-state">선택한 날짜에 완료 기록이 없습니다.</p>
            ) : (
              <>
                <p className="detail-calendar__selected-title">
                  {new Date(`${selectedDateKey}T00:00:00`).toLocaleDateString('ko-KR')} 완료 이력
                </p>
                <ul className="detail-history">
                  {selectedCompletions.map((completion) => (
                    <li key={completion.id} className="detail-history__item">
                      <strong>{formatDateTime(completion.completedAt)} 완료</strong>
                      <span>이전 예정 {formatDateTime(completion.previousDueAt)}</span>
                      <span>다음 예정 {formatDateTime(completion.nextDueAt)}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        </section>
      </div>
    </section>
  )
}
