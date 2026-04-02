import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/auth'
import { extractErrorMessage } from '../api/client'
import type { PendingUser } from '../types/auth'
import { ErrorNotice } from './ErrorNotice'

type ActionKind = 'approve' | 'reject'

interface ActionState {
  userId: number
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
  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionState, setActionState] = useState<ActionState | null>(null)

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

  const submitAction = useCallback(async (userId: number, kind: ActionKind) => {
    if (actionState) return

    setActionState({ userId, kind })
    setNotice(null)

    try {
      const res =
        kind === 'approve'
          ? await authApi.approveUser(userId)
          : await authApi.rejectUser(userId)

      if (res.success) {
        setPendingUsers((prev) => prev.filter((user) => user.userId !== userId))
        setNotice(kind === 'approve' ? '사용자를 승인했습니다.' : '사용자를 거절했습니다.')
      } else {
        setError(extractErrorMessage(res.error, kind === 'approve' ? '승인 처리에 실패했습니다.' : '거절 처리에 실패했습니다.'))
      }
    } finally {
      setActionState(null)
    }
  }, [actionState])

  const isActionBusy = actionState !== null
  const pendingCount = useMemo(() => pendingUsers.length, [pendingUsers.length])

  return (
    <section className="admin-approvals" aria-labelledby="admin-approvals-title">
      <div className="admin-approvals__header">
        <div>
          <h2 id="admin-approvals-title">관리자 승인</h2>
          <p>승인 대기 계정을 확인하고 승인/거절 처리할 수 있습니다.</p>
        </div>
        <button type="button" className="btn-secondary" onClick={loadPendingUsers} disabled={isLoading || isActionBusy}>
          {isLoading ? '불러오는 중...' : '새로고침'}
        </button>
      </div>

      {notice && <p className="admin-approvals__notice" role="status" aria-live="polite">{notice}</p>}
      {error && <ErrorNotice message={error} onRetry={loadPendingUsers} />}

      {isLoading ? (
        <p className="app-loading">승인 대기 사용자 목록을 불러오는 중...</p>
      ) : pendingCount === 0 ? (
        <p className="admin-approvals__empty">승인 대기 중인 사용자가 없습니다.</p>
      ) : (
        <ul className="admin-approvals__list">
          {pendingUsers.map((user) => {
            const isApproving = actionState?.userId === user.userId && actionState.kind === 'approve'
            const isRejecting = actionState?.userId === user.userId && actionState.kind === 'reject'
            const isDisabled = isActionBusy

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
                    onClick={() => submitAction(user.userId, 'approve')}
                  >
                    {isApproving ? '승인 중...' : '승인'}
                  </button>
                  <button
                    type="button"
                    className="admin-approvals__reject"
                    disabled={isDisabled}
                    onClick={() => submitAction(user.userId, 'reject')}
                  >
                    {isRejecting ? '거절 중...' : '거절'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
