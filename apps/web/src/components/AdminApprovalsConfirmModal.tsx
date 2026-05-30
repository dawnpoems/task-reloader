import { useRef } from 'react'
import { useModalFocusTrap } from '../hooks/useModalFocusTrap'
import type { ActionKind, ActionState, ConfirmTarget } from '../hooks/useAdminApprovals'

interface AdminApprovalsConfirmModalProps {
  confirmTarget: ConfirmTarget
  actionState: ActionState | null
  isActionBusy: boolean
  restoreFocusRef: React.MutableRefObject<HTMLElement | null>
  onClose: () => void
  onSubmit: () => void
}

function getActionLabel(kind: ActionKind): string {
  if (kind === 'approve') return '승인'
  if (kind === 'reject') return '거절'
  if (kind === 'set-approved') return '승인 상태로 변경'
  return '거절 상태로 변경'
}

function getActionButtonClass(kind: ActionKind): string {
  if (kind === 'reject' || kind === 'set-rejected') return 'admin-approvals__reject'
  return 'admin-approvals__approve'
}

function getActionSubmitLabel(kind: ActionKind): string {
  if (kind === 'approve') return '승인 진행'
  if (kind === 'reject') return '거절 진행'
  if (kind === 'set-approved') return '승인으로 변경'
  return '거절로 변경'
}

function getActionSubmittingLabel(kind: ActionKind): string {
  if (kind === 'approve') return '승인 처리 중...'
  if (kind === 'reject') return '거절 처리 중...'
  if (kind === 'set-approved') return '승인으로 변경 중...'
  return '거절로 변경 중...'
}

export function AdminApprovalsConfirmModal({
  confirmTarget,
  actionState,
  isActionBusy,
  restoreFocusRef,
  onClose,
  onSubmit,
}: AdminApprovalsConfirmModalProps) {
  const confirmButtonRef = useRef<HTMLButtonElement | null>(null)
  const confirmActionLabel = getActionLabel(confirmTarget.kind)
  const confirmActionButtonClass = getActionButtonClass(confirmTarget.kind)
  const confirmSubmitLabel = getActionSubmitLabel(confirmTarget.kind)
  const confirmSubmittingLabel = getActionSubmittingLabel(confirmTarget.kind)
  const isConfirmingCurrentAction =
    actionState?.userId === confirmTarget.user.userId &&
    actionState?.kind === confirmTarget.kind
  const { modalRef, handleKeyDown } = useModalFocusTrap<HTMLDivElement>({
    isOpen: true,
    onRequestClose: onClose,
    isCloseDisabled: isActionBusy,
    initialFocusRef: confirmButtonRef,
    restoreFocusRef,
  })

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        ref={modalRef}
        className="modal admin-approvals__modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-approvals-confirm-title"
        aria-describedby="admin-approvals-confirm-description"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <div className="modal__header">
          <h2 id="admin-approvals-confirm-title">{confirmActionLabel} 확인</h2>
          <button className="modal__close" onClick={onClose} aria-label="닫기" disabled={isActionBusy}>
            ✕
          </button>
        </div>

        <div className="modal__body">
          <p id="admin-approvals-confirm-description" className="admin-approvals__modal-message">
            아래 사용자를 정말 {confirmActionLabel}할까요?
            <strong className="admin-approvals__modal-email">{confirmTarget.user.email}</strong>
          </p>

          <div className="modal__actions admin-approvals__modal-actions">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isActionBusy}>
              취소
            </button>
            <button
              ref={confirmButtonRef}
              type="button"
              className={confirmActionButtonClass}
              onClick={onSubmit}
              disabled={isActionBusy}
            >
              {isConfirmingCurrentAction ? confirmSubmittingLabel : confirmSubmitLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
