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

function sortByCreatedAtAsc(users: PendingUser[]): PendingUser[] {
  return [...users].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
}

export function AdminApprovalsPage() {
  const searchInputId = useId()
  const confirmTitleId = useId()
  const confirmDescriptionId = useId()
  const modalRef = useRef<HTMLDivElement | null>(null)
  const confirmButtonRef = useRef<HTMLButtonElement | null>(null)
  const previousFocusedElementRef = useRef<HTMLElement | null>(null)

  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([])
  const [nonPendingUsers, setNonPendingUsers] = useState<PendingUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isNonPendingLoading, setIsNonPendingLoading] = useState(false)
  const [isNonPendingOpen, setIsNonPendingOpen] = useState(false)
  const [isNonPendingLoaded, setIsNonPendingLoaded] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [nonPendingError, setNonPendingError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionState, setActionState] = useState<ActionState | null>(null)
  const [searchEmail, setSearchEmail] = useState('')
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget | null>(null)

  const loadPendingUsers = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)

    const res = await authApi.getPendingUsers()
    if (res.success && res.data) {
      setPendingUsers(res.data)
    } else {
      setLoadError(extractErrorMessage(res.error, '승인 대기 사용자 목록을 불러오지 못했습니다.'))
    }
    setIsLoading(false)
  }, [])

  const loadNonPendingUsers = useCallback(async () => {
    setIsNonPendingLoading(true)
    setNonPendingError(null)

    const res = await authApi.getNonPendingUsers()
    if (res.success && res.data) {
      setNonPendingUsers(sortByCreatedAtAsc(res.data))
      setIsNonPendingLoaded(true)
    } else {
      setNonPendingUsers([])
      setNonPendingError(extractErrorMessage(res.error, '승인/거절 사용자 목록을 불러오지 못했습니다.'))
    }

    setIsNonPendingLoading(false)
  }, [])

  useEffect(() => {
    loadPendingUsers()
  }, [loadPendingUsers])

  useEffect(() => {
    if (!isNonPendingOpen || isNonPendingLoaded || isNonPendingLoading) return
    loadNonPendingUsers()
  }, [isNonPendingLoaded, isNonPendingLoading, isNonPendingOpen, loadNonPendingUsers])

  const refreshUsers = useCallback(async () => {
    await loadPendingUsers()
    if (isNonPendingOpen || isNonPendingLoaded) {
      await loadNonPendingUsers()
    }
  }, [isNonPendingLoaded, isNonPendingOpen, loadNonPendingUsers, loadPendingUsers])

  const openConfirmModal = useCallback((user: PendingUser, kind: ActionKind, trigger: HTMLElement | null) => {
    if (actionState) return
    setActionError(null)
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
    setActionError(null)

    try {
      const res =
        kind === 'approve'
          ? await authApi.approveUser(userId)
          : await authApi.rejectUser(userId)

      if (res.success) {
        const changedUser = res.data
        setPendingUsers((prev) => prev.filter((user) => user.userId !== userId))
        if (changedUser && isNonPendingLoaded) {
          setNonPendingUsers((prev) => {
            const merged = prev.filter((user) => user.userId !== changedUser.userId)
            merged.push(changedUser)
            return sortByCreatedAtAsc(merged)
          })
        }
        setNotice(kind === 'approve' ? '사용자를 승인했습니다.' : '사용자를 거절했습니다.')
        setConfirmTarget(null)
      } else {
        setActionError(
          extractErrorMessage(
            res.error,
            kind === 'approve'
              ? '승인 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'
              : '거절 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'
          )
        )
      }
    } finally {
      setActionState(null)
    }
  }, [actionState, confirmTarget, isNonPendingLoaded])

  const normalizedSearch = searchEmail.trim().toLowerCase()
  const filteredPendingUsers = useMemo(
    () => pendingUsers.filter((user) => user.email.toLowerCase().includes(normalizedSearch)),
    [normalizedSearch, pendingUsers]
  )
  const filteredNonPendingUsers = useMemo(
    () => nonPendingUsers.filter((user) => user.email.toLowerCase().includes(normalizedSearch)),
    [normalizedSearch, nonPendingUsers]
  )

  const isActionBusy = actionState !== null
  const isConfirmOpen = confirmTarget !== null
  const isInteractionLocked = isActionBusy || isConfirmOpen
  const pendingCount = pendingUsers.length
  const filteredPendingCount = filteredPendingUsers.length
  const nonPendingCount = nonPendingUsers.length
  const filteredNonPendingCount = filteredNonPendingUsers.length

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
        <button
          type="button"
          className="btn-secondary"
          onClick={refreshUsers}
          disabled={isLoading || isNonPendingLoading || isInteractionLocked}
        >
          {isLoading || isNonPendingLoading ? '불러오는 중...' : '새로고침'}
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
        <p className="admin-approvals__summary">승인 대기 {pendingCount}명 · 표시 {filteredPendingCount}명</p>
      </div>

      {notice && <p className="admin-approvals__notice" role="status" aria-live="polite">{notice}</p>}
      {loadError && <ErrorNotice message={loadError} onRetry={loadPendingUsers} />}
      {actionError && <p className="admin-approvals__action-error" role="alert" aria-live="assertive">{actionError}</p>}

      {isLoading ? (
        <p className="app-loading">승인 대기 사용자 목록을 불러오는 중...</p>
      ) : loadError ? null : pendingCount === 0 ? (
        <p className="admin-approvals__empty">승인 대기 중인 사용자가 없습니다.</p>
      ) : filteredPendingCount === 0 ? (
        <p className="admin-approvals__empty admin-approvals__empty--filter">검색 조건과 일치하는 사용자가 없습니다.</p>
      ) : (
        <ul className="admin-approvals__list">
          {filteredPendingUsers.map((user) => {
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

      <section className="admin-approvals__secondary">
        <button
          type="button"
          className="btn-secondary admin-approvals__secondary-toggle"
          onClick={() => setIsNonPendingOpen((prev) => !prev)}
          disabled={isInteractionLocked || isNonPendingLoading}
        >
          {isNonPendingOpen ? '승인/거절 사용자 접기' : `승인/거절 사용자 보기${isNonPendingLoaded ? ` (${nonPendingCount})` : ''}`}
        </button>

        {isNonPendingOpen && (
          <div className="admin-approvals__secondary-content">
            <p className="admin-approvals__summary">승인/거절 {nonPendingCount}명 · 표시 {filteredNonPendingCount}명</p>
            {nonPendingError && <ErrorNotice message={nonPendingError} onRetry={loadNonPendingUsers} />}

            {isNonPendingLoading ? (
              <p className="app-loading">승인/거절 사용자 목록을 불러오는 중...</p>
            ) : nonPendingError ? null : nonPendingCount === 0 ? (
              <p className="admin-approvals__empty">승인/거절된 사용자가 없습니다.</p>
            ) : filteredNonPendingCount === 0 ? (
              <p className="admin-approvals__empty admin-approvals__empty--filter">검색 조건과 일치하는 사용자가 없습니다.</p>
            ) : (
              <ul className="admin-approvals__list">
                {filteredNonPendingUsers.map((user) => (
                  <li key={user.userId} className="admin-approvals__item">
                    <div className="admin-approvals__meta">
                      <p className="admin-approvals__email">{user.email}</p>
                      <p className="admin-approvals__detail">
                        역할: {user.role} · 상태: {user.status} · 가입일: {formatCreatedAt(user.createdAt)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>

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
