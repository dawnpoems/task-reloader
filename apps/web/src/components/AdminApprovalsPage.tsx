import { useId } from 'react'
import { useAdminApprovals } from '../hooks/useAdminApprovals'
import { AdminApprovalsConfirmModal } from './AdminApprovalsConfirmModal'
import { PendingApprovalList, ReviewedApprovalSection } from './AdminApprovalsUserLists'
import { ErrorNotice } from './ErrorNotice'

export function AdminApprovalsPage() {
  const searchInputId = useId()
  const {
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
    isInteractionLocked,
    pendingCount,
    filteredPendingCount,
    nonPendingCount,
    filteredNonPendingCount,
    toggleNonPendingOpen,
  } = useAdminApprovals()

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
        <PendingApprovalList
          users={filteredPendingUsers}
          actionState={actionState}
          isInteractionLocked={isInteractionLocked}
          onOpenConfirm={openConfirmModal}
        />
      )}

      <ReviewedApprovalSection
        users={filteredNonPendingUsers}
        actionState={actionState}
        isOpen={isNonPendingOpen}
        isLoaded={isNonPendingLoaded}
        isLoading={isNonPendingLoading}
        error={nonPendingError}
        isInteractionLocked={isInteractionLocked}
        totalCount={nonPendingCount}
        filteredCount={filteredNonPendingCount}
        onToggle={toggleNonPendingOpen}
        onRetry={loadNonPendingUsers}
        onOpenConfirm={openConfirmModal}
      />

      {confirmTarget && (
        <AdminApprovalsConfirmModal
          confirmTarget={confirmTarget}
          actionState={actionState}
          isActionBusy={isActionBusy}
          restoreFocusRef={previousFocusedElementRef}
          onClose={requestCloseConfirmModal}
          onSubmit={submitAction}
        />
      )}
    </section>
  )
}
