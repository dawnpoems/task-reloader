import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../api/client'
import { tasksApi } from '../api/tasks'
import { formatDate, formatDateTime } from '../lib/utils'
import type { Task } from '../types/task'
import type { TaskCompletion } from '../types/taskCompletion'

interface TaskDetailPageProps {
  taskId: number
  onBack: () => void
  onEdit: (task: Task) => void
  onComplete: (id: number) => Promise<boolean>
}

export function TaskDetailPage({ taskId, onBack, onEdit, onComplete }: TaskDetailPageProps) {
  const [task, setTask] = useState<Task | null>(null)
  const [completions, setCompletions] = useState<TaskCompletion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const fetchDetail = async () => {
      setIsLoading(true)
      setError(null)

      const [taskRes, completionsRes] = await Promise.all([
        tasksApi.getById(taskId),
        tasksApi.getCompletions(taskId),
      ])

      if (!active) return

      if (taskRes.success && taskRes.data) {
        setTask(taskRes.data)
      } else {
        setTask(null)
        setError(extractErrorMessage(taskRes.error, 'Task 상세 정보를 불러오지 못했습니다.'))
      }

      if (completionsRes.success && completionsRes.data) {
        setCompletions(completionsRes.data)
      } else {
        setCompletions([])
        setError((prev) => prev ?? extractErrorMessage(completionsRes.error, '완료 이력을 불러오지 못했습니다.'))
      }

      setIsLoading(false)
    }

    fetchDetail()
    return () => { active = false }
  }, [taskId])

  const handleComplete = async () => {
    setActionError(null)
    const ok = await onComplete(taskId)
    if (!ok) {
      setActionError('완료 처리 후 상세 정보를 갱신하지 못했습니다.')
      return
    }

    const [taskRes, completionsRes] = await Promise.all([
      tasksApi.getById(taskId),
      tasksApi.getCompletions(taskId),
    ])

    if (taskRes.success && taskRes.data) setTask(taskRes.data)
    if (completionsRes.success && completionsRes.data) setCompletions(completionsRes.data)
  }

  if (isLoading) {
    return <p className="app-loading">상세 정보를 불러오는 중...</p>
  }

  if (error || !task) {
    return (
      <section className="detail-page">
        <button type="button" className="btn-secondary detail-page__back" onClick={onBack}>
          목록으로
        </button>
        <p className="app-error">{error ?? 'Task를 찾을 수 없습니다.'}</p>
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
            <button type="button" className="btn-complete" onClick={handleComplete}>
              완료
            </button>
          )}
          <button type="button" className="btn-edit" onClick={() => onEdit(task)}>
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

      {actionError && <p className="app-error">{actionError}</p>}

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
          <h3>완료 이력</h3>
          {completions.length === 0 ? (
            <p className="section-state">아직 완료 이력이 없습니다.</p>
          ) : (
            <ul className="detail-history">
              {completions.map((completion) => (
                <li key={completion.id} className="detail-history__item">
                  <strong>{formatDateTime(completion.completedAt)} 완료</strong>
                  <span>이전 예정 {formatDateTime(completion.previousDueAt)}</span>
                  <span>다음 예정 {formatDateTime(completion.nextDueAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </section>
  )
}
