import { useId, useLayoutEffect, useRef } from 'react'
import type { CreateTaskRequest } from '../types/task'
import { TaskForm } from './TaskForm'

interface TaskCreateModalProps {
  onSubmit: (request: CreateTaskRequest) => Promise<boolean>
  onClose: () => void
}

export function TaskCreateModal({ onSubmit, onClose }: TaskCreateModalProps) {
  const titleId = useId()
  const modalRef = useRef<HTMLDivElement | null>(null)
  const previouslyFocusedElementRef = useRef<HTMLElement | null>(
    document.activeElement instanceof HTMLElement ? document.activeElement : null
  )

  useLayoutEffect(() => {
    const previousFocusTarget = previouslyFocusedElementRef.current
    const nameInput = modalRef.current?.querySelector<HTMLInputElement>('#name')
    nameInput?.focus()

    return () => {
      if (!previousFocusTarget) return
      window.requestAnimationFrame(() => {
        previousFocusTarget.focus()
      })
    }
  }, [])

  const requestClose = () => {
    onClose()
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
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
