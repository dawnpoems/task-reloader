import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { tasksApi } from '../api/tasks'
import { extractErrorMessage } from '../api/client'
import { formatDateTime } from '../lib/utils'
import type { Task, UpdateTaskRequest } from '../types/task'
import type { TaskCompletion } from '../types/taskCompletion'

interface TaskEditModalProps {
  task: Task
  onUpdate: (id: number, request: UpdateTaskRequest) => Promise<boolean>
  onDelete: (id: number) => Promise<boolean>
  onClose: () => void
}

const todayDateInput = (): string => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function TaskEditModal({ task, onUpdate, onDelete, onClose }: TaskEditModalProps) {
  const titleId = useId()
  const modalRef = useRef<HTMLDivElement | null>(null)
  const nameInputRef = useRef<HTMLInputElement | null>(null)
  const previouslyFocusedElementRef = useRef<HTMLElement | null>(
    document.activeElement instanceof HTMLElement ? document.activeElement : null
  )
  const [name, setName] = useState(task.name)
  const [everyNDays, setEveryNDays] = useState(task.everyNDays)
  const [startDate, setStartDate] = useState(task.startDate ?? todayDateInput())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [completions, setCompletions] = useState<TaskCompletion[]>([])
  const [isLoadingCompletions, setIsLoadingCompletions] = useState(true)
  const [completionsError, setCompletionsError] = useState<string | null>(null)

  useLayoutEffect(() => {
    const previousFocusTarget = previouslyFocusedElementRef.current
    const rafId = window.requestAnimationFrame(() => {
      nameInputRef.current?.focus()
    })
    const timeoutId = window.setTimeout(() => {
      nameInputRef.current?.focus()
    }, 60)

    return () => {
      window.cancelAnimationFrame(rafId)
      window.clearTimeout(timeoutId)
      if (!previousFocusTarget) return
      window.requestAnimationFrame(() => {
        previousFocusTarget.focus()
      })
    }
  }, [])

  useEffect(() => {
    let active = true

    const fetchCompletions = async () => {
      setIsLoadingCompletions(true)
      setCompletionsError(null)

      const res = await tasksApi.getCompletions(task.id)
      if (!active) return

      if (res.success && res.data) {
        setCompletions(res.data)
      } else {
        setCompletions([])
        setCompletionsError(extractErrorMessage(res.error, '완료 이력을 불러오지 못했습니다.'))
      }
      setIsLoadingCompletions(false)
    }

    fetchCompletions()
    return () => { active = false }
  }, [task.id])

  const requestClose = () => {
    onClose()
  }

  const handleModalKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      requestClose()
      return
    }

    if (e.key !== 'Tab') return

    const modalEl = modalRef.current
    if (!modalEl) return

    const focusables = Array.from(
      modalEl.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    ).filter((el) => !el.hasAttribute('disabled') && el.tabIndex >= 0)

    if (focusables.length === 0) return

    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    const active = document.activeElement as HTMLElement | null

    if (e.shiftKey) {
      if (active === first || !modalEl.contains(active)) {
        e.preventDefault()
        last.focus()
      }
      return
    }

    if (active === last) {
      e.preventDefault()
      first.focus()
    }
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) { setError('이름을 입력해 주세요.'); return }
    if (everyNDays < 1) { setError('반복 주기는 1일 이상이어야 합니다.'); return }
    setIsSubmitting(true)
    setError(null)
    const ok = await onUpdate(task.id, { name: name.trim(), everyNDays, startDate: startDate || undefined })
    setIsSubmitting(false)
    if (ok) requestClose()
    else setError('수정에 실패했습니다.')
  }

  const handleDelete = async () => {
    const confirmed = window.confirm('정말 삭제할까요?')
    if (!confirmed) return

    setIsDeleting(true)
    const ok = await onDelete(task.id)
    setIsDeleting(false)
    if (ok) requestClose()
  }

  return (
    // 백드롭 클릭 시 닫기
    <div className="modal-backdrop" onClick={requestClose}>
      <div
        ref={modalRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleModalKeyDown}
      >
        <div className="modal__header">
          <h2 id={titleId}>Task 수정</h2>
          <button className="modal__close" onClick={requestClose} aria-label="닫기">✕</button>
        </div>

        <form onSubmit={handleUpdate} className="modal__body">
          {error && <p className="task-form__error">{error}</p>}

          <div className="task-form__field">
            <label htmlFor="edit-name">이름 *</label>
            <input
              id="edit-name"
              type="text"
              value={name}
              ref={nameInputRef}
              autoFocus
              onChange={(e) => setName(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="task-form__field">
            <label htmlFor="edit-everyNDays">반복 주기 (일) *</label>
            <input
              id="edit-everyNDays"
              type="number"
              min={1}
              value={everyNDays}
              onChange={(e) => setEveryNDays(Number(e.target.value))}
              disabled={isSubmitting}
            />
          </div>

          <div className="task-form__field">
            <label htmlFor="edit-startDate">시작 날짜</label>
            <input
              id="edit-startDate"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="modal__actions">
            <button
              type="button"
              className="btn-delete"
              onClick={handleDelete}
              disabled={isDeleting || isSubmitting}
            >
              {isDeleting ? '삭제 중...' : '삭제'}
            </button>
            <div className="modal__actions-main">
              <button
                type="button"
                className="btn-secondary"
                onClick={requestClose}
                disabled={isSubmitting || isDeleting}
              >
                취소
              </button>
              <button type="submit" disabled={isSubmitting || isDeleting}>
                {isSubmitting ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </form>

        <section className="modal__history">
          <div className="modal__history-header">
            <h3>최근 완료 이력</h3>
            <span>{completions.length}건</span>
          </div>

          {isLoadingCompletions ? (
            <p className="modal__history-state">불러오는 중...</p>
          ) : completionsError ? (
            <p className="modal__history-error">{completionsError}</p>
          ) : completions.length === 0 ? (
            <p className="modal__history-state">아직 완료 이력이 없습니다.</p>
          ) : (
            <ul className="modal__history-list">
              {completions.slice(0, 5).map((completion) => (
                <li key={completion.id} className="modal__history-item">
                  <div className="modal__history-item-top">
                    <strong>{formatDateTime(completion.completedAt)} 완료</strong>
                  </div>
                  <div className="modal__history-item-meta">
                    <span>이전 예정 {formatDateTime(completion.previousDueAt)}</span>
                    <span>다음 예정 {formatDateTime(completion.nextDueAt)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
