import { useId, useRef, useState } from 'react'
import { useModalFocusTrap } from '../hooks/useModalFocusTrap'
import { useTaskCompletions } from '../hooks/useTaskCompletions'
import { formatDate, formatDateTime } from '../lib/utils'
import type { Task, UpdateTaskRequest } from '../types/task'

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
  const nameInputRef = useRef<HTMLInputElement | null>(null)
  const [name, setName] = useState(task.name)
  const [everyNDays, setEveryNDays] = useState(task.everyNDays)
  const [startDate, setStartDate] = useState(task.startDate ?? todayDateInput())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isBusy = isSubmitting || isDeleting
  const {
    completions,
    isLoading: isLoadingCompletions,
    error: completionsError,
    refetch: fetchCompletions,
  } = useTaskCompletions(task.id, {
    errorMessage: '완료 이력을 불러오지 못했습니다. 다시 시도해 주세요.',
  })

  const requestClose = () => {
    if (isBusy) return
    onClose()
  }

  const { modalRef, handleKeyDown: handleModalKeyDown } = useModalFocusTrap<HTMLDivElement>({
    onRequestClose: requestClose,
    isCloseDisabled: isBusy,
    initialFocusRef: nameInputRef,
  })

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) { setError('이름을 입력해 주세요.'); return }
    if (everyNDays < 1) { setError('반복 주기는 1일 이상이어야 합니다.'); return }
    setIsSubmitting(true)
    setError(null)
    const ok = await onUpdate(task.id, { name: name.trim(), everyNDays, startDate: startDate || undefined })
    setIsSubmitting(false)
    if (ok) requestClose()
    else setError('수정에 실패했습니다. 잠시 후 다시 시도해 주세요.')
  }

  const handleDelete = async () => {
    const confirmed = window.confirm('정말 삭제할까요?')
    if (!confirmed) return
    if (isSubmitting) return

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
          <button className="modal__close" onClick={requestClose} aria-label="닫기" disabled={isBusy}>✕</button>
        </div>

        <form onSubmit={handleUpdate} className="modal__body">
          {error && <p className="task-form__error" role="alert" aria-live="assertive">{error}</p>}

          <div className="task-form__field">
            <label htmlFor="edit-name">이름 *</label>
            <input
              id="edit-name"
              type="text"
              value={name}
              ref={nameInputRef}
              autoFocus
              onChange={(e) => setName(e.target.value)}
              disabled={isBusy}
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
              disabled={isBusy}
            />
          </div>

          <div className="task-form__field">
            <label htmlFor="edit-startDate">시작 날짜</label>
            <input
              id="edit-startDate"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              disabled={isBusy}
            />
          </div>

          <div className="modal__actions">
            <button
              type="button"
              className="btn-delete"
              onClick={handleDelete}
              disabled={isBusy}
            >
              {isDeleting ? '삭제 중...' : '삭제'}
            </button>
            <div className="modal__actions-main">
              <button
                type="button"
                className="btn-secondary"
                onClick={requestClose}
                disabled={isBusy}
              >
                취소
              </button>
              <button type="submit" disabled={isBusy}>
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
            <div>
              <p className="modal__history-error" role="alert" aria-live="assertive">{completionsError}</p>
              <button type="button" className="btn-secondary" onClick={fetchCompletions}>
                다시 시도
              </button>
            </div>
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
                    <span>다음 예정 {formatDate(completion.nextDueAt)}</span>
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
