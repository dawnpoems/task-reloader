import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { authApi } from '../api/auth'
import { extractErrorMessage } from '../api/client'
import type { PendingUser, UserStatus } from '../types/auth'

export type ActionKind = 'approve' | 'reject' | 'set-approved' | 'set-rejected'

export interface ActionState {
  userId: number
  kind: ActionKind
}

export interface ConfirmTarget {
  user: PendingUser
  kind: ActionKind
}

function sortByCreatedAtAsc(users: PendingUser[]): PendingUser[] {
  return [...users].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
}

function getActionSuccessNotice(kind: ActionKind): string {
  if (kind === 'approve') return '사용자를 승인했습니다.'
  if (kind === 'reject') return '사용자를 거절했습니다.'
  if (kind === 'set-approved') return '사용자 상태를 승인으로 변경했습니다.'
  return '사용자 상태를 거절로 변경했습니다.'
}

function getActionFailureMessage(kind: ActionKind): string {
  if (kind === 'approve') return '승인 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'
  if (kind === 'reject') return '거절 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'
  if (kind === 'set-approved') return '승인 상태 변경에 실패했습니다. 잠시 후 다시 시도해 주세요.'
  return '거절 상태 변경에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}

function getTargetStatus(kind: ActionKind): UserStatus | null {
  if (kind === 'set-approved') return 'APPROVED'
  if (kind === 'set-rejected') return 'REJECTED'
  return null
}

export function isStatusToggleAction(kind: ActionKind): boolean {
  return kind === 'set-approved' || kind === 'set-rejected'
}

export function useAdminApprovals() {
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
    if (res.success) {
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
    if (res.success) {
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
          : kind === 'reject'
            ? await authApi.rejectUser(userId)
            : await authApi.updateUserStatus(userId, getTargetStatus(kind) ?? 'APPROVED')

      if (res.success) {
        const nextStatus = getTargetStatus(kind)
        const changedUser = res.data ?? (nextStatus ? { ...confirmTarget.user, status: nextStatus } : null)

        if (!isStatusToggleAction(kind)) {
          setPendingUsers((prev) => prev.filter((user) => user.userId !== userId))
          if (changedUser && isNonPendingLoaded) {
            setNonPendingUsers((prev) => {
              const merged = prev.filter((user) => user.userId !== changedUser.userId)
              merged.push(changedUser)
              return sortByCreatedAtAsc(merged)
            })
          }
        } else if (changedUser) {
          setNonPendingUsers((prev) => sortByCreatedAtAsc(
            prev.map((user) => (user.userId === changedUser.userId ? changedUser : user))
          ))
        }
        setNotice(getActionSuccessNotice(kind))
        setConfirmTarget(null)
      } else {
        setActionError(extractErrorMessage(res.error, getActionFailureMessage(kind)))
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

  return {
    pendingUsers,
    nonPendingUsers,
    isLoading,
    isNonPendingLoading,
    isNonPendingOpen,
    isNonPendingLoaded,
    loadError,
    nonPendingError,
    actionError,
    notice,
    actionState,
    searchEmail,
    setSearchEmail,
    confirmTarget,
    previousFocusedElementRef,
    loadPendingUsers,
    loadNonPendingUsers,
    refreshUsers,
    openConfirmModal,
    requestCloseConfirmModal,
    submitAction,
    filteredPendingUsers,
    filteredNonPendingUsers,
    isActionBusy,
    isConfirmOpen,
    isInteractionLocked,
    pendingCount: pendingUsers.length,
    filteredPendingCount: filteredPendingUsers.length,
    nonPendingCount: nonPendingUsers.length,
    filteredNonPendingCount: filteredNonPendingUsers.length,
    toggleNonPendingOpen: () => setIsNonPendingOpen((prev) => !prev),
  }
}
