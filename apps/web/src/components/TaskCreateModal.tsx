import { useId } from 'react'
import { useModalFocusTrap } from '../hooks/useModalFocusTrap'
import type { CreateTaskRequest } from '../types/task'
import { TaskForm } from './TaskForm'

interface TaskCreateModalProps {
  onSubmit: (request: CreateTaskRequest) => Promise<boolean>
  onClose: () => void
}

export function TaskCreateModal({ onSubmit, onClose }: TaskCreateModalProps) {
  const titleId = useId()

  const requestClose = () => {
    onClose()
  }

  const { modalRef, handleKeyDown } = useModalFocusTrap<HTMLDivElement>({
    onRequestClose: requestClose,
    initialFocusSelector: '#name',
  })

  return (
    <div className="modal-backdrop" onClick={requestClose}>
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
          <button className="modal__close" onClick={requestClose} aria-label="닫기">✕</button>
        </div>
        <TaskForm onSubmit={onSubmit} onCancel={requestClose} hideTitle />
      </div>
    </div>
  )
}
