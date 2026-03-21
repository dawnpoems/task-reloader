import { useEffect, useId, useRef } from 'react'
import type { CreateTaskRequest } from '../types/task'
import { TaskForm } from './TaskForm'

interface TaskCreateModalProps {
  onSubmit: (request: CreateTaskRequest) => Promise<boolean>
  onClose: () => void
}

export function TaskCreateModal({ onSubmit, onClose }: TaskCreateModalProps) {
  const titleId = useId()
  const modalRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    modalRef.current?.focus()
  }, [])

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      onClose()
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        ref={modalRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <div className="modal__header">
          <h2 id={titleId}>새 Task 추가</h2>
          <button className="modal__close" onClick={onClose} aria-label="닫기">✕</button>
        </div>
        <TaskForm onSubmit={onSubmit} onCancel={onClose} hideTitle />
      </div>
    </div>
  )
}
