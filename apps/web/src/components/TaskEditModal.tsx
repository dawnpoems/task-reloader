import { useState } from 'react'
import type { Task, UpdateTaskRequest } from '../types/task'

interface TaskEditModalProps {
  task: Task
  onUpdate: (id: number, request: UpdateTaskRequest) => Promise<boolean>
  onDelete: (id: number) => Promise<boolean>
  onClose: () => void
}

export function TaskEditModal({ task, onUpdate, onDelete, onClose }: TaskEditModalProps) {
  const [name, setName] = useState(task.name)
  const [everyNDays, setEveryNDays] = useState(task.everyNDays)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) { setError('이름을 입력해 주세요.'); return }
    if (everyNDays < 1) { setError('반복 주기는 1일 이상이어야 합니다.'); return }
    setIsSubmitting(true)
    setError(null)
    const ok = await onUpdate(task.id, { name: name.trim(), everyNDays })
    setIsSubmitting(false)
    if (ok) onClose()
    else setError('수정에 실패했습니다.')
  }

  const handleDelete = async () => {
    setIsDeleting(true)
    const ok = await onDelete(task.id)
    setIsDeleting(false)
    if (ok) onClose()
  }

  return (
    // 백드롭 클릭 시 닫기
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal__header">
          <h2>Task 수정</h2>
          <button className="modal__close" onClick={onClose} aria-label="닫기">✕</button>
        </div>

        <form onSubmit={handleUpdate} className="modal__body">
          {error && <p className="task-form__error">{error}</p>}

          <div className="task-form__field">
            <label htmlFor="edit-name">이름 *</label>
            <input
              id="edit-name"
              type="text"
              value={name}
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

          <div className="modal__actions">
            <button
              type="button"
              className="btn-secondary"
              onClick={onClose}
              disabled={isSubmitting}
            >
              취소
            </button>
            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '저장 중...' : '저장'}
            </button>
          </div>
        </form>

        <div className="modal__danger-zone">
          {!confirmDelete ? (
            <button
              className="btn-delete"
              onClick={() => setConfirmDelete(true)}
              disabled={isDeleting}
            >
              삭제
            </button>
          ) : (
            <div className="modal__confirm-delete">
              <span>정말 삭제할까요?</span>
              <button
                className="btn-delete"
                onClick={handleDelete}
                disabled={isDeleting}
              >
                {isDeleting ? '삭제 중...' : '확인'}
              </button>
              <button
                className="btn-secondary"
                onClick={() => setConfirmDelete(false)}
                disabled={isDeleting}
              >
                취소
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

