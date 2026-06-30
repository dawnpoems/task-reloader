import { useEffect, useRef, useState } from 'react'
import { useTasks } from './hooks/useTasks'
import { useInsights } from './hooks/useInsights'
import { useDashboardSummary } from './hooks/useDashboardSummary'
import { useBrowserNavigation } from './hooks/useBrowserNavigation'
import { useUpcomingTasks } from './hooks/useUpcomingTasks'
import { useTaskWorkflow } from './hooks/useTaskWorkflow'
import { InsightsPage } from './components/InsightsPage'
import { ErrorNotice } from './components/ErrorNotice'
import { HomePage } from './components/HomePage'
import { TaskDetailPage } from './components/TaskDetailPage'
import { TaskCreateModal } from './components/TaskCreateModal'
import { TaskEditModal } from './components/TaskEditModal'
import { AuthLoginPage } from './components/AuthLoginPage'
import { AuthSignupPage } from './components/AuthSignupPage'
import { AdminApprovalsPage } from './components/AdminApprovalsPage'
import { AlertSettingsPage } from './components/AlertSettingsPage'
import { tasksApi } from './api/tasks'
import { extractErrorMessage } from './api/client'
import { useAuth } from './auth/AuthContext'
import { getAuthNoticeMessage, popAuthNotice } from './auth/authNotice'
import {
  ADMIN_APPROVALS_PATH,
  ALERT_SETTINGS_PATH,
  HOME_PATH,
  INSIGHTS_PATH,
  LOGIN_PATH,
  SIGNUP_PATH,
  canAccessPathByRole,
  clearPostLoginRedirect,
  getTaskIdFromPath,
  isKnownPath,
  isPublicPath,
  loginPathWithEmailPrefill,
  popPostLoginRedirect,
  savePostLoginRedirect,
} from './lib/routes'
import type { Task } from './types/task'
import './App.css'

function App() {
  const { user, isAuthenticated, isInitializing, login, signup, logout } = useAuth()
  const { pathname, navigateTo, replaceTo } = useBrowserNavigation()
  const [showForm, setShowForm] = useState(false)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [restoreCreateButtonFocus, setRestoreCreateButtonFocus] = useState(false)
  const [loginNotice, setLoginNotice] = useState<string | null>(null)
  const createTaskButtonRef = useRef<HTMLButtonElement | null>(null)

  const selectedTaskId = getTaskIdFromPath(pathname)
  const isHomePage = pathname === HOME_PATH
  const isInsightsPage = pathname === INSIGHTS_PATH
  const isAlertSettingsPage = pathname === ALERT_SETTINGS_PATH
  const isAdminApprovalsPage = pathname === ADMIN_APPROVALS_PATH
  const isDataEnabled = isAuthenticated && !isInitializing
  const canViewAdminPage = user?.role === 'ADMIN'

  const {
    tasks: dueNowTasks,
    isLoading,
    error,
    toast,
    createTask,
    updateTask,
    completeTask,
    deleteTask,
    deleteTaskCompletion,
    refetch,
  } = useTasks('DUE_NOW', isDataEnabled)

  const {
    dashboard: insightsDashboard,
    overview,
    todayCompletions,
    isLoading: isInsightsLoading,
    error: insightsError,
    refetch: refetchInsights,
  } = useInsights(isInsightsPage && isDataEnabled)
  const {
    dashboard,
    error: dashboardError,
    refetch: refetchDashboard,
  } = useDashboardSummary(isHomePage && isDataEnabled)
  const {
    tasks: upcomingTasks,
    isOpen: isUpcomingOpen,
    isLoaded: isUpcomingLoaded,
    isLoading: isUpcomingLoading,
    error: upcomingError,
    fetchTasks: fetchUpcomingTasks,
    toggleOpen: toggleUpcomingOpen,
  } = useUpcomingTasks(isDataEnabled)
  const {
    completingTaskIds,
    completedTaskIds,
    detailRefreshToken,
    refreshAll,
    handleCreateTask,
    handleUpdateTask,
    handleDeleteTask,
    handleCompleteTask,
    handleCompleteTaskFromDetail,
    handleDeleteTaskCompletionFromDetail,
  } = useTaskWorkflow({
    isDataEnabled,
    selectedTaskId,
    isUpcomingLoaded,
    refetchDueNow: refetch,
    refetchInsights,
    refetchDashboard,
    fetchUpcomingTasks,
    createTask,
    updateTask,
    completeTask,
    deleteTask,
    deleteTaskCompletion,
    onCreateSuccess: () => setShowForm(false),
    onSelectedTaskDeleted: () => navigateTo(HOME_PATH),
  })

  useEffect(() => {
    if (!restoreCreateButtonFocus || showForm) return
    const rafId = window.requestAnimationFrame(() => {
      createTaskButtonRef.current?.focus()
      setRestoreCreateButtonFocus(false)
    })
    return () => window.cancelAnimationFrame(rafId)
  }, [restoreCreateButtonFocus, showForm])

  useEffect(() => {
    if (pathname !== LOGIN_PATH) {
      setLoginNotice(null)
      return
    }

    const noticeCode = popAuthNotice()
    if (!noticeCode) return
    setLoginNotice(getAuthNoticeMessage(noticeCode))
  }, [pathname])

  useEffect(() => {
    if (isInitializing) return

    if (!isKnownPath(pathname)) {
      replaceTo(isAuthenticated ? HOME_PATH : LOGIN_PATH)
      return
    }

    if (!isAuthenticated) {
      if (!isPublicPath(pathname)) {
        savePostLoginRedirect(pathname)
        replaceTo(loginPathWithEmailPrefill())
      }
      return
    }

    if (isPublicPath(pathname)) {
      const redirectPath = popPostLoginRedirect()
      if (
        redirectPath &&
        isKnownPath(redirectPath) &&
        !isPublicPath(redirectPath) &&
        canAccessPathByRole(redirectPath, user?.role)
      ) {
        replaceTo(redirectPath)
      } else {
        replaceTo(HOME_PATH)
      }
      return
    }

    if (isAdminApprovalsPage && !canViewAdminPage) {
      replaceTo(HOME_PATH)
    }
  }, [canViewAdminPage, isAdminApprovalsPage, isAuthenticated, isInitializing, pathname, replaceTo, user?.role])

  useEffect(() => {
    if (isDataEnabled) return

    setShowForm(false)
    setSelectedTask(null)
  }, [isDataEnabled])

  const handleEditTaskFromInsights = async (id: number) => {
    const res = await tasksApi.getById(id)
    if (res.success) {
      setSelectedTask(res.data)
      return
    }
    window.alert(extractErrorMessage(res.error, 'Task 정보를 불러오지 못했습니다.'))
  }

  const handleCloseCreateModal = () => {
    setShowForm(false)
    setRestoreCreateButtonFocus(true)
  }

  const handleLogout = async () => {
    await logout()
    clearPostLoginRedirect()
    replaceTo(LOGIN_PATH)
  }

  const shouldShowGlobalError = isHomePage && !showForm && !selectedTask && !selectedTaskId

  if (isInitializing) {
    return (
      <div className="app">
        <p className="app-loading">인증 상태를 확인하는 중...</p>
      </div>
    )
  }

  if (pathname === LOGIN_PATH) {
    return (
      <AuthLoginPage
        onLogin={login}
        onGoSignup={() => navigateTo(SIGNUP_PATH)}
        noticeMessage={loginNotice}
        onDismissNotice={() => setLoginNotice(null)}
      />
    )
  }

  if (pathname === SIGNUP_PATH) {
    return <AuthSignupPage onSignup={signup} onGoLogin={() => navigateTo(LOGIN_PATH)} />
  }

  if (!isAuthenticated || !user) {
    return (
      <div className="app">
        <p className="app-loading">로그인 화면으로 이동하는 중...</p>
      </div>
    )
  }

  if (user.status !== 'APPROVED') {
    const statusMessage =
      user.status === 'PENDING'
        ? '계정이 관리자 승인 대기 상태입니다. 승인 완료 후 다시 이용해 주세요.'
        : '계정 승인이 거절된 상태입니다. 관리자에게 문의해 주세요.'

    return (
      <div className="auth-page">
        <section className="auth-card account-status-card" aria-live="polite">
          <h1>계정 상태 확인</h1>
          <p className="auth-card__subtitle">{statusMessage}</p>
          <button type="button" onClick={handleLogout}>
            로그아웃
          </button>
        </section>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Task Reloader</h1>
          <p className="subtitle">A completion-driven recurring task scheduler</p>
        </div>
        <div className="app-header__actions">
          <div className="app-user-chip" aria-live="polite">
            <strong>{user.email}</strong>
            <span>{user.role === 'ADMIN' ? '관리자' : '일반 사용자'}</span>
          </div>
          <nav className="app-nav">
            <button
              type="button"
              className={`app-nav__link ${isHomePage ? 'app-nav__link--active' : ''}`}
              onClick={() => navigateTo(HOME_PATH)}
            >
              Task
            </button>
            <button
              type="button"
              className={`app-nav__link ${isInsightsPage ? 'app-nav__link--active' : ''}`}
              onClick={() => navigateTo(INSIGHTS_PATH)}
            >
              인사이트
            </button>
            <button
              type="button"
              className={`app-nav__link ${isAlertSettingsPage ? 'app-nav__link--active' : ''}`}
              onClick={() => navigateTo(ALERT_SETTINGS_PATH)}
            >
              알림 설정
            </button>
            {canViewAdminPage && (
              <button
                type="button"
                className={`app-nav__link ${isAdminApprovalsPage ? 'app-nav__link--active' : ''}`}
                onClick={() => navigateTo(ADMIN_APPROVALS_PATH)}
              >
                관리자
              </button>
            )}
          </nav>
          <button type="button" className="btn-secondary" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </header>

      <main className="app-main">
        {shouldShowGlobalError && error && <ErrorNotice message={error} onRetry={refreshAll} />}
        {shouldShowGlobalError && !error && dashboardError && <ErrorNotice message={dashboardError} onRetry={refreshAll} />}
        {toast && <p className="app-toast" role="status" aria-live="polite">{toast}</p>}

        {isAdminApprovalsPage ? (
          <AdminApprovalsPage />
        ) : isAlertSettingsPage ? (
          <AlertSettingsPage />
        ) : selectedTaskId ? (
          <TaskDetailPage
            taskId={selectedTaskId}
            refreshToken={detailRefreshToken}
            onBack={() => navigateTo(HOME_PATH)}
            onEdit={setSelectedTask}
            onComplete={handleCompleteTaskFromDetail}
            onDeleteCompletion={handleDeleteTaskCompletionFromDetail}
          />
        ) : isInsightsPage ? (
          <InsightsPage
            dashboard={insightsDashboard}
            overview={overview}
            todayCompletions={todayCompletions}
            isLoading={isInsightsLoading}
            error={insightsError}
            onOpenTask={(taskId) => navigateTo(`/tasks/${taskId}`)}
            onEditTask={handleEditTaskFromInsights}
            onDeleteTask={handleDeleteTask}
            onRetry={refetchInsights}
          />
        ) : (
          <HomePage
            dashboard={dashboard}
            dueNowTasks={dueNowTasks}
            dueNowError={error}
            isLoading={isLoading}
            showForm={showForm}
            createTaskButtonRef={createTaskButtonRef}
            upcomingTasks={upcomingTasks}
            isUpcomingOpen={isUpcomingOpen}
            isUpcomingLoading={isUpcomingLoading}
            upcomingError={upcomingError}
            completingTaskIds={completingTaskIds}
            completedTaskIds={completedTaskIds}
            onCreateTaskClick={() => setShowForm(true)}
            onToggleUpcoming={toggleUpcomingOpen}
            onRetryUpcoming={fetchUpcomingTasks}
            onCompleteTask={handleCompleteTask}
            onEditTask={setSelectedTask}
            onViewTask={(task) => navigateTo(`/tasks/${task.id}`)}
          />
        )}
      </main>

      {selectedTask && (
        <TaskEditModal
          task={selectedTask}
          onUpdate={handleUpdateTask}
          onDelete={handleDeleteTask}
          onClose={() => setSelectedTask(null)}
        />
      )}
      {!selectedTaskId && !isInsightsPage && !isAlertSettingsPage && !isAdminApprovalsPage && showForm && (
        <TaskCreateModal
          onSubmit={handleCreateTask}
          onClose={handleCloseCreateModal}
        />
      )}
    </div>
  )
}

export default App
