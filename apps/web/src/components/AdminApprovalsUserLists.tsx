import { isStatusToggleAction } from '../hooks/useAdminApprovals'
import type { ActionKind, ActionState } from '../hooks/useAdminApprovals'
import type { PendingUser } from '../types/auth'
import { ErrorNotice } from './ErrorNotice'

interface PendingApprovalListProps {
  users: PendingUser[]
  actionState: ActionState | null
  isInteractionLocked: boolean
  onOpenConfirm: (user: PendingUser, kind: ActionKind, trigger: HTMLElement | null) => void
}

interface ReviewedApprovalSectionProps {
  users: PendingUser[]
  actionState: ActionState | null
  isOpen: boolean
  isLoaded: boolean
  isLoading: boolean
  error: string | null
  isInteractionLocked: boolean
  totalCount: number
  filteredCount: number
  onToggle: () => void
  onRetry: () => void
  onOpenConfirm: (user: PendingUser, kind: ActionKind, trigger: HTMLElement | null) => void
}

function formatCreatedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function PendingApprovalList({
  users,
  actionState,
  isInteractionLocked,
  onOpenConfirm,
}: PendingApprovalListProps) {
  return (
    <ul className="admin-approvals__list">
      {users.map((user) => {
        const isApproving = actionState?.userId === user.userId && actionState.kind === 'approve'
        const isRejecting = actionState?.userId === user.userId && actionState.kind === 'reject'

        return (
          <li key={user.userId} className="admin-approvals__item">
            <div className="admin-approvals__meta">
              <p className="admin-approvals__email">{user.email}</p>
              <p className="admin-approvals__detail">
                역할: {user.role} · 상태: {user.status} · 가입일: {formatCreatedAt(user.createdAt)}
              </p>
            </div>
            <div className="admin-approvals__actions admin-approvals__actions--decision">
              <button
                type="button"
                className="admin-approvals__approve"
                disabled={isInteractionLocked}
                onClick={(event) => onOpenConfirm(user, 'approve', event.currentTarget)}
              >
                {isApproving ? '승인 중...' : '승인'}
              </button>
              <button
                type="button"
                className="admin-approvals__reject"
                disabled={isInteractionLocked}
                onClick={(event) => onOpenConfirm(user, 'reject', event.currentTarget)}
              >
                {isRejecting ? '거절 중...' : '거절'}
              </button>
            </div>
          </li>
        )
      })}
    </ul>
  )
}

export function ReviewedApprovalSection({
  users,
  actionState,
  isOpen,
  isLoaded,
  isLoading,
  error,
  isInteractionLocked,
  totalCount,
  filteredCount,
  onToggle,
  onRetry,
  onOpenConfirm,
}: ReviewedApprovalSectionProps) {
  return (
    <section className="admin-approvals__secondary">
      <button
        type="button"
        className="btn-secondary admin-approvals__secondary-toggle"
        onClick={onToggle}
        disabled={isInteractionLocked || isLoading}
      >
        {isOpen ? '승인/거절 사용자 접기' : `승인/거절 사용자 보기${isLoaded ? ` (${totalCount})` : ''}`}
      </button>

      {isOpen && (
        <div className="admin-approvals__secondary-content">
          <p className="admin-approvals__summary">승인/거절 {totalCount}명 · 표시 {filteredCount}명</p>
          {error && <ErrorNotice message={error} onRetry={onRetry} />}

          {isLoading ? (
            <p className="app-loading">승인/거절 사용자 목록을 불러오는 중...</p>
          ) : error ? null : totalCount === 0 ? (
            <p className="admin-approvals__empty">승인/거절된 사용자가 없습니다.</p>
          ) : filteredCount === 0 ? (
            <p className="admin-approvals__empty admin-approvals__empty--filter">검색 조건과 일치하는 사용자가 없습니다.</p>
          ) : (
            <ul className="admin-approvals__list">
              {users.map((user) => {
                const toggleKind: ActionKind = user.status === 'APPROVED' ? 'set-rejected' : 'set-approved'
                const isToggling = actionState?.userId === user.userId && isStatusToggleAction(actionState.kind)
                const isApproved = user.status === 'APPROVED'
                const toggleLabel = isApproved ? '승인' : '거절'
                const toggleHint = isApproved ? '클릭하면 거절로 변경' : '클릭하면 승인으로 변경'

                return (
                  <li key={user.userId} className="admin-approvals__item">
                    <div className="admin-approvals__meta">
                      <p className="admin-approvals__email">{user.email}</p>
                      <p className="admin-approvals__detail">
                        역할: {user.role} · 상태: {user.status} · 가입일: {formatCreatedAt(user.createdAt)}
                      </p>
                    </div>
                    <div className="admin-approvals__actions admin-approvals__actions--toggle">
                      <button
                        type="button"
                        className={`admin-approvals__status-toggle ${isApproved ? 'admin-approvals__status-toggle--approved' : 'admin-approvals__status-toggle--rejected'}`}
                        aria-label={`${user.email} 상태를 ${isApproved ? '거절' : '승인'}으로 변경`}
                        disabled={isInteractionLocked}
                        onClick={(event) => onOpenConfirm(user, toggleKind, event.currentTarget)}
                      >
                        <span className="admin-approvals__status-toggle-track" aria-hidden="true">
                          <span className="admin-approvals__status-toggle-thumb" />
                        </span>
                        <span>{isToggling ? '변경 중...' : toggleLabel}</span>
                      </button>
                      <p className="admin-approvals__toggle-hint">{toggleHint}</p>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
