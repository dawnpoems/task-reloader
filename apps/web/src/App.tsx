import { useCallback, useEffect, useRef, useState } from 'react'
import { useTasks } from './hooks/useTasks'
import { useInsights } from './hooks/useInsights'
import { InsightsPage } from './components/InsightsPage'
import { ErrorNotice } from './components/ErrorNotice'
import { TaskSection } from './components/TaskSection'
import { TaskDetailPage } from './components/TaskDetailPage'
import { TaskCreateModal } from './components/TaskCreateModal'
import { TaskEditModal } from './components/TaskEditModal'
import { AuthLoginPage } from './components/AuthLoginPage'
import { AuthSignupPage } from './components/AuthSignupPage'
import { AdminApprovalsPage } from './components/AdminApprovalsPage'
import { tasksApi } from './api/tasks'
import { extractErrorMessage } from './api/client'
import { useAuth } from './auth/AuthContext'
import { getAuthNoticeMessage, popAuthNotice } from './auth/authNotice'
import type { Task } from './types/task'
import './App.css'

const HOME_PATH = '/'
const INSIGHTS_PATH = '/insights'
const LOGIN_PATH = '/auth/login'
const SIGNUP_PATH = '/auth/signup'
const ADMIN_APPROVALS_PATH = '/admin/approvals'
const POST_LOGIN_REDIRECT_KEY = 'task_reloader.post_login_redirect'

const getTaskIdFromPath = (pathname: string): number | null => {
  const match = pathname.match(/^\/tasks\/(\d+)$/)
  return match ? Number(match[1]) : null
}

const isPublicPath = (pathname: string): boolean => pathname === LOGIN_PATH || pathname === SIGNUP_PATH

const canAccessPathByRole = (pathname: string, role?: 'USER' | 'ADMIN'): boolean => {
  if (pathname === ADMIN_APPROVALS_PATH) return role === 'ADMIN'
  return true
}

const isKnownPath = (pathname: string): boolean => {
  if (pathname === HOME_PATH) return true
  if (pathname === INSIGHTS_PATH) return true
  if (pathname === LOGIN_PATH) return true
  if (pathname === SIGNUP_PATH) return true
  if (pathname === ADMIN_APPROVALS_PATH) return true
  return getTaskIdFromPath(pathname) !== null
}

const savePostLoginRedirect = (pathname: string): void => {
  if (!isKnownPath(pathname) || isPublicPath(pathname)) return
  window.sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, pathname)
}

const popPostLoginRedirect = (): string | null => {
  const stored = window.sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY)
  if (!stored) return null
  window.sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
  return stored
}

const clearPostLoginRedirect = (): void => {
  window.sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
}

function App() {
  const { user, isAuthenticated, isInitializing, login, signup, logout } = useAuth()
  const [pathname, setPathname] = useState(window.location.pathname)
  const [showForm, setShowForm] = useState(false)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [upcomingTasks, setUpcomingTasks] = useState<Task[]>([])
  const [isUpcomingOpen, setIsUpcomingOpen] = useState(false)
  const [isUpcomingLoaded, setIsUpcomingLoaded] = useState(false)
  const [isUpcomingLoading, setIsUpcomingLoading] = useState(false)
  const [upcomingError, setUpcomingError] = useState<string | null>(null)
  const [completingTaskIds, setCompletingTaskIds] = useState<Set<number>>(new Set())
  const [completedTaskIds, setCompletedTaskIds] = useState<Set<number>>(new Set())
  const [detailRefreshToken, setDetailRefreshToken] = useState(0)
  const [restoreCreateButtonFocus, setRestoreCreateButtonFocus] = useState(false)
  const [loginNotice, setLoginNotice] = useState<string | null>(null)
  const createTaskButtonRef = useRef<HTMLButtonElement | null>(null)

  const selectedTaskId = getTaskIdFromPath(pathname)
  const isHomePage = pathname === HOME_PATH
  const isInsightsPage = pathname === INSIGHTS_PATH
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
    refetch,
  } = useTasks('DUE_NOW', isDataEnabled)

  const {
    dashboard,
    overview,
    recentCompletions,
    isLoading: isInsightsLoading,
    error: insightsError,
    refetch: refetchInsights,
  } = useInsights(isInsightsPage && isDataEnabled)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

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

  const navigateTo = useCallback((nextPath: string) => {
    if (nextPath === window.location.pathname) {
      setPathname(nextPath)
      return
    }
    window.history.pushState({}, '', nextPath)
    setPathname(nextPath)
  }, [])

  const replaceTo = useCallback((nextPath: string) => {
    if (nextPath === window.location.pathname) {
      setPathname(nextPath)
      return
    }
    window.history.replaceState({}, '', nextPath)
    setPathname(nextPath)
  }, [])

  useEffect(() => {
    if (isInitializing) return

    if (!isKnownPath(pathname)) {
      replaceTo(isAuthenticated ? HOME_PATH : LOGIN_PATH)
      return
    }

    if (!isAuthenticated) {
      if (!isPublicPath(pathname)) {
        savePostLoginRedirect(pathname)
        replaceTo(LOGIN_PATH)
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
    setIsUpcomingOpen(false)
    setIsUpcomingLoaded(false)
    setIsUpcomingLoading(false)
    setUpcomingError(null)
    setUpcomingTasks([])
    setCompletingTaskIds(new Set())
    setCompletedTaskIds(new Set())
  }, [isDataEnabled])

  const fetchUpcomingTasks = useCallback(async () => {
    if (!isDataEnabled) return

    setIsUpcomingLoading(true)
    setUpcomingError(null)
    const res = await tasksApi.getAll('UPCOMING')
    if (res.success && res.data) {
      setUpcomingTasks(res.data)
      setIsUpcomingLoaded(true)
    } else {
      setUpcomingError(extractErrorMessage(res.error, '남은 일정을 불러오지 못했습니다.'))
    }
    setIsUpcomingLoading(false)
  }, [isDataEnabled])

  useEffect(() => {
    if (isUpcomingOpen && !isUpcomingLoaded && !isUpcomingLoading) {
      fetchUpcomingTasks()
    }
  }, [isUpcomingOpen, isUpcomingLoaded, isUpcomingLoading, fetchUpcomingTasks])

  const refreshAll = async () => {
    if (!isDataEnabled) return

    const tasksToRefresh = [refetch(), refetchInsights()]
    if (isUpcomingLoaded) {
      tasksToRefresh.push(fetchUpcomingTasks())
    }
    await Promise.all(tasksToRefresh)
  }

  const handleCreateTask = async (req: Parameters<typeof createTask>[0]) => {
    const ok = await createTask(req)
    if (ok) {
      await refreshAll()
      setShowForm(false)
    }
    return ok
  }

  const handleUpdateTask = async (id: number, request: Parameters<typeof updateTask>[1]) => {
    const ok = await updateTask(id, request)
    if (ok) {
      await refreshAll()
      setDetailRefreshToken((prev) => prev + 1)
    }
    return ok
  }

  const handleDeleteTask = async (id: number) => {
    const ok = await deleteTask(id)
    if (ok) {
      await refreshAll()
      if (selectedTaskId === id) navigateTo(HOME_PATH)
    }
    return ok
  }

  const handleCompleteTask = async (id: number) => {
    setCompletingTaskIds((prev) => {
      const next = new Set(prev)
      next.add(id)
      return next
    })

    try {
      const ok = await completeTask(id)
      if (ok) {
        setCompletedTaskIds((prev) => {
          const next = new Set(prev)
          next.add(id)
          return next
        })

        await new Promise((resolve) => setTimeout(resolve, 1150))
        await refreshAll()
      }
      return ok
    } finally {
      setCompletingTaskIds((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      setCompletedTaskIds((prev) => {
        if (!prev.has(id)) return prev
        const next = new Set(prev)
        next.delete(id)
        return next
      })
    }
  }

  const handleCompleteTaskFromDetail = async (id: number) => {
    const ok = await completeTask(id)
    if (!ok) return false

    const tasksToRefresh = [refetch()]
    if (isUpcomingLoaded) {
      tasksToRefresh.push(fetchUpcomingTasks())
    }
    await Promise.all(tasksToRefresh)
    return true
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
        {toast && <p className="app-toast" role="status" aria-live="polite">{toast}</p>}

        {isAdminApprovalsPage ? (
          <AdminApprovalsPage />
        ) : selectedTaskId ? (
          <TaskDetailPage
            taskId={selectedTaskId}
            refreshToken={detailRefreshToken}
            onBack={() => navigateTo(HOME_PATH)}
            onEdit={setSelectedTask}
            onComplete={handleCompleteTaskFromDetail}
          />
        ) : isInsightsPage ? (
          <InsightsPage
            dashboard={dashboard}
            overview={overview}
            recentCompletions={recentCompletions}
            isLoading={isInsightsLoading}
            error={insightsError}
            onOpenTask={(taskId) => navigateTo(`/tasks/${taskId}`)}
            onRetry={refetchInsights}
          />
        ) : isLoading ? (
          <p className="app-loading">불러오는 중...</p>
        ) : (
          <section className="insights-section">
            <div className="section-header">
              <div>
                <h2>오늘 할 일</h2>
              </div>
              {!showForm && (
                <button
                  ref={createTaskButtonRef}
                  type="button"
                  className="btn-secondary section-header__task-toggle"
                  onClick={() => setShowForm(true)}
                >
                  + 새 Task
                </button>
              )}
            </div>
            {error ? null : dueNowTasks.length === 0 ? (
              <p className="today-all-done">오늘 할일을 모두 마쳤어요!</p>
            ) : (
              <TaskSection
                tasks={dueNowTasks}
                onComplete={handleCompleteTask}
                onEdit={setSelectedTask}
                onView={(task) => navigateTo(`/tasks/${task.id}`)}
                completingTaskIds={completingTaskIds}
                completedTaskIds={completedTaskIds}
              />
            )}

            <div className="section-collapse">
              <button
                type="button"
                className="btn-secondary section-collapse__trigger"
                onClick={() => setIsUpcomingOpen((prev) => !prev)}
              >
                {isUpcomingOpen ? '남은 일정 접기' : `남은 일정 펼치기${dashboard ? ` (${dashboard.upcomingTasks})` : ''}`}
              </button>

              {isUpcomingOpen && (
                <div className="section-collapse__content">
                  {upcomingError && <ErrorNotice message={upcomingError} onRetry={fetchUpcomingTasks} />}
                  {isUpcomingLoading ? (
                    <p className="app-loading">남은 일정을 불러오는 중...</p>
                  ) : (
                    <TaskSection
                      tasks={upcomingTasks}
                      onComplete={handleCompleteTask}
                      onEdit={setSelectedTask}
                      onView={(task) => navigateTo(`/tasks/${task.id}`)}
                      completingTaskIds={completingTaskIds}
                      completedTaskIds={completedTaskIds}
                    />
                  )}
                </div>
              )}
            </div>
          </section>
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
      {!selectedTaskId && !isInsightsPage && !isAdminApprovalsPage && showForm && (
        <TaskCreateModal
          onSubmit={handleCreateTask}
          onClose={handleCloseCreateModal}
        />
      )}
    </div>
  )
}

export default App
