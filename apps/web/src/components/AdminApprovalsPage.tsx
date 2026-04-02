import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { authApi } from '../api/auth'
import { extractErrorMessage } from '../api/client'
import type { PendingUser } from '../types/auth'
import { ErrorNotice } from './ErrorNotice'

type ActionKind = 'approve' | 'reject'

interface ActionState {
  userId: number
  kind: ActionKind
}

interface ConfirmTarget {
  user: PendingUser
  kind: ActionKind
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

export function AdminApprovalsPage() {
  const searchInputId = useId()
  const confirmTitleId = useId()
  const confirmDescriptionId = useId()
  const modalRef = useRef<HTMLDivElement | null>(null)
  const confirmButtonRef = useRef<HTMLButtonElement | null>(null)
  const previousFocusedElementRef = useRef<HTMLElement | null>(null)

  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionState, setActionState] = useState<ActionState | null>(null)
  const [searchEmail, setSearchEmail] = useState('')
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget | null>(null)

  const loadPendingUsers = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    const res = await authApi.getPendingUsers()
    if (res.success && res.data) {
      setPendingUsers(res.data)
    } else {
      setPendingUsers([])
      setError(extractErrorMessage(res.error, '승인 대기 사용자 목록을 불러오지 못했습니다.'))
    }
    setIsLoading(false)
  }, [])

  useEffect(() => {
    loadPendingUsers()
  }, [loadPendingUsers])

  const openConfirmModal = useCallback((user: PendingUser, kind: ActionKind, trigger: HTMLElement | null) => {
    if (actionState) return
    previousFocusedElementRef.current = trigger ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null)
    setConfirmTarget({ user, kind })
  }, [actionState])

  const requestCloseConfirmModal = useCallback(() => {
    if (actionState) return
    setConfirmTarget(null)
  }, [actionState])

  useLayoutEffect(() => {
    if (!confirmTarget) return

    const rafId = window.requestAnimationFrame(() => {
      confirmButtonRef.current?.focus()
    })
    const timeoutId = window.setTimeout(() => {
      confirmButtonRef.current?.focus()
    }, 60)

    return () => {
      window.cancelAnimationFrame(rafId)
      window.clearTimeout(timeoutId)
      const previous = previousFocusedElementRef.current
      if (!previous) return
      window.requestAnimationFrame(() => {
        previous.focus()
      })
    }
  }, [confirmTarget])

  const handleConfirmModalKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      if (actionState) return
      e.preventDefault()
      requestCloseConfirmModal()
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

  const submitAction = useCallback(async () => {
    if (!confirmTarget || actionState) return

    const userId = confirmTarget.user.userId
    const kind = confirmTarget.kind

    setActionState({ userId, kind })
    setNotice(null)
    setError(null)

    try {
      const res =
        kind === 'approve'
          ? await authApi.approveUser(userId)
          : await authApi.rejectUser(userId)

      if (res.success) {
        setPendingUsers((prev) => prev.filter((user) => user.userId !== userId))
        setNotice(kind === 'approve' ? '사용자를 승인했습니다.' : '사용자를 거절했습니다.')
        setConfirmTarget(null)
      } else {
        setError(extractErrorMessage(res.error, kind === 'approve' ? '승인 처리에 실패했습니다.' : '거절 처리에 실패했습니다.'))
      }
    } finally {
      setActionState(null)
    }
  }, [actionState, confirmTarget])

  const normalizedSearch = searchEmail.trim().toLowerCase()
  const filteredUsers = useMemo(
    () => pendingUsers.filter((user) => user.email.toLowerCase().includes(normalizedSearch)),
    [normalizedSearch, pendingUsers]
  )

  const isActionBusy = actionState !== null
  const isConfirmOpen = confirmTarget !== null
  const isInteractionLocked = isActionBusy || isConfirmOpen
  const pendingCount = useMemo(() => pendingUsers.length, [pendingUsers.length])
  const filteredCount = filteredUsers.length

  const confirmActionLabel = confirmTarget?.kind === 'approve' ? '승인' : '거절'
  const isConfirmingApprove = actionState?.kind === 'approve'
  const isConfirmingReject = actionState?.kind === 'reject'

  return (
    <section className="admin-approvals" aria-labelledby="admin-approvals-title">
      <div className="admin-approvals__header">
        <div>
          <h2 id="admin-approvals-title">관리자 승인</h2>
          <p>승인 대기 계정을 확인하고 승인/거절 처리할 수 있습니다.</p>
        </div>
        <button type="button" className="btn-secondary" onClick={loadPendingUsers} disabled={isLoading || isInteractionLocked}>
          {isLoading ? '불러오는 중...' : '새로고침'}
        </button>
      </div>

      <div className="admin-approvals__filter">
        <label htmlFor={searchInputId}>이메일 검색</label>
        <div className="admin-approvals__filter-control">
          <input
            id={searchInputId}
            type="text"
            value={searchEmail}
            onChange={(event) => setSearchEmail(event.target.value)}
            placeholder="예: user@example.com"
            disabled={isInteractionLocked}
          />
          {searchEmail && (
            <button
              type="button"
              className="btn-secondary admin-approvals__clear"
              onClick={() => setSearchEmail('')}
              disabled={isInteractionLocked}
            >
              검색 지우기
            </button>
          )}
        </div>
        <p className="admin-approvals__summary">전체 {pendingCount}명 · 표시 {filteredCount}명</p>
      </div>

      {notice && <p className="admin-approvals__notice" role="status" aria-live="polite">{notice}</p>}
      {error && <ErrorNotice message={error} onRetry={loadPendingUsers} />}

      {isLoading ? (
        <p className="app-loading">승인 대기 사용자 목록을 불러오는 중...</p>
      ) : pendingCount === 0 ? (
        <p className="admin-approvals__empty">승인 대기 중인 사용자가 없습니다.</p>
      ) : filteredCount === 0 ? (
        <p className="admin-approvals__empty admin-approvals__empty--filter">검색 조건과 일치하는 사용자가 없습니다.</p>
      ) : (
        <ul className="admin-approvals__list">
          {filteredUsers.map((user) => {
            const isApproving = actionState?.userId === user.userId && actionState.kind === 'approve'
            const isRejecting = actionState?.userId === user.userId && actionState.kind === 'reject'
            const isDisabled = isInteractionLocked

            return (
              <li key={user.userId} className="admin-approvals__item">
                <div className="admin-approvals__meta">
                  <p className="admin-approvals__email">{user.email}</p>
                  <p className="admin-approvals__detail">
                    역할: {user.role} · 상태: {user.status} · 가입일: {formatCreatedAt(user.createdAt)}
                  </p>
                </div>
                <div className="admin-approvals__actions">
                  <button
                    type="button"
                    className="admin-approvals__approve"
                    disabled={isDisabled}
                    onClick={(event) => openConfirmModal(user, 'approve', event.currentTarget)}
                  >
                    {isApproving ? '승인 중...' : '승인'}
                  </button>
                  <button
                    type="button"
                    className="admin-approvals__reject"
                    disabled={isDisabled}
                    onClick={(event) => openConfirmModal(user, 'reject', event.currentTarget)}
                  >
                    {isRejecting ? '거절 중...' : '거절'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {confirmTarget && (
        <div className="modal-backdrop" onClick={requestCloseConfirmModal}>
          <div
            ref={modalRef}
            className="modal admin-approvals__modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby={confirmTitleId}
            aria-describedby={confirmDescriptionId}
            tabIndex={-1}
            onClick={(event) => event.stopPropagation()}
            onKeyDown={handleConfirmModalKeyDown}
          >
            <div className="modal__header">
              <h2 id={confirmTitleId}>{confirmActionLabel} 확인</h2>
              <button className="modal__close" onClick={requestCloseConfirmModal} aria-label="닫기" disabled={isActionBusy}>
                ✕
              </button>
            </div>

            <div className="modal__body">
              <p id={confirmDescriptionId} className="admin-approvals__modal-message">
                아래 사용자를 정말 {confirmActionLabel}할까요?
                <strong className="admin-approvals__modal-email">{confirmTarget.user.email}</strong>
              </p>

              <div className="modal__actions admin-approvals__modal-actions">
                <button type="button" className="btn-secondary" onClick={requestCloseConfirmModal} disabled={isActionBusy}>
                  취소
                </button>
                <button
                  ref={confirmButtonRef}
                  type="button"
                  className={confirmTarget.kind === 'approve' ? 'admin-approvals__approve' : 'admin-approvals__reject'}
                  onClick={submitAction}
                  disabled={isActionBusy}
                >
                  {confirmTarget.kind === 'approve'
                    ? isConfirmingApprove ? '승인 처리 중...' : '승인 진행'
                    : isConfirmingReject ? '거절 처리 중...' : '거절 진행'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
