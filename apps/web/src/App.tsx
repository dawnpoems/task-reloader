import { useCallback, useEffect, useState } from 'react'
import { useTasks } from './hooks/useTasks'
import { useInsights } from './hooks/useInsights'
import { InsightsPage } from './components/InsightsPage'
import { TaskSection } from './components/TaskSection'
import { TaskDetailPage } from './components/TaskDetailPage'
import { TaskCreateModal } from './components/TaskCreateModal'
import { TaskEditModal } from './components/TaskEditModal'
import { tasksApi } from './api/tasks'
import { extractErrorMessage } from './api/client'
import type { Task } from './types/task'
import './App.css'

const INSIGHTS_PATH = '/insights'

const getTaskIdFromPath = (pathname: string): number | null => {
  const match = pathname.match(/^\/tasks\/(\d+)$/)
  return match ? Number(match[1]) : null
}

function App() {
  const { tasks: dueNowTasks, isLoading, error, toast, createTask, updateTask, completeTask, deleteTask, refetch } = useTasks('DUE_NOW')
  const { dashboard, recentCompletions, isLoading: isInsightsLoading, error: insightsError, refetch: refetchInsights } = useInsights()
  const [showForm, setShowForm] = useState(false)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [pathname, setPathname] = useState(window.location.pathname)
  const [upcomingTasks, setUpcomingTasks] = useState<Task[]>([])
  const [isUpcomingOpen, setIsUpcomingOpen] = useState(false)
  const [isUpcomingLoaded, setIsUpcomingLoaded] = useState(false)
  const [isUpcomingLoading, setIsUpcomingLoading] = useState(false)
  const [upcomingError, setUpcomingError] = useState<string | null>(null)
  const [completingTaskIds, setCompletingTaskIds] = useState<Set<number>>(new Set())
  const [completedTaskIds, setCompletedTaskIds] = useState<Set<number>>(new Set())
  const [detailRefreshToken, setDetailRefreshToken] = useState(0)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigateTo = (nextPath: string) => {
    if (nextPath === window.location.pathname) return
    window.history.pushState({}, '', nextPath)
    setPathname(nextPath)
  }

  const selectedTaskId = getTaskIdFromPath(pathname)
  const isInsightsPage = pathname === INSIGHTS_PATH
  const isHomePage = pathname === '/'

  const fetchUpcomingTasks = useCallback(async () => {
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
  }, [])

  useEffect(() => {
    if (isUpcomingOpen && !isUpcomingLoaded && !isUpcomingLoading) {
      fetchUpcomingTasks()
    }
  }, [isUpcomingOpen, isUpcomingLoaded, isUpcomingLoading, fetchUpcomingTasks])

  const refreshAll = async () => {
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
      if (selectedTaskId === id) navigateTo('/')
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

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Task Reloader</h1>
          <p className="subtitle">A completion-driven recurring task scheduler</p>
        </div>
        <div className="app-header__actions">
          <nav className="app-nav">
            <button
              type="button"
              className={`app-nav__link ${isHomePage ? 'app-nav__link--active' : ''}`}
              onClick={() => navigateTo('/')}
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
          </nav>
        </div>
      </header>

      <main className="app-main">
        {error && <p className="app-error">{error}</p>}
        {toast && <p className="app-toast">{toast}</p>}

        {selectedTaskId ? (
          <TaskDetailPage
            taskId={selectedTaskId}
            refreshToken={detailRefreshToken}
            onBack={() => navigateTo('/')}
            onEdit={setSelectedTask}
            onComplete={handleCompleteTask}
          />
        ) : isInsightsPage ? (
          <InsightsPage
            dashboard={dashboard}
            recentCompletions={recentCompletions}
            isLoading={isInsightsLoading}
            error={insightsError}
            onOpenTask={(taskId) => navigateTo(`/tasks/${taskId}`)}
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
                  type="button"
                  className="btn-secondary section-header__task-toggle"
                  onClick={() => setShowForm(true)}
                >
                  + 새 Task
                </button>
              )}
            </div>
            {dueNowTasks.length === 0 ? (
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
                  {upcomingError && <p className="app-error">{upcomingError}</p>}
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
      {!selectedTaskId && !isInsightsPage && showForm && (
        <TaskCreateModal
          onSubmit={handleCreateTask}
          onClose={() => setShowForm(false)}
        />
      )}
    </div>
  )
}

export default App
