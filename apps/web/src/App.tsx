import { useEffect, useState } from 'react'
import { useTasks } from './hooks/useTasks'
import { useInsights } from './hooks/useInsights'
import { InsightsPage } from './components/InsightsPage'
import { TaskSection } from './components/TaskSection'
import { TaskDetailPage } from './components/TaskDetailPage'
import { TaskForm } from './components/TaskForm'
import { TaskEditModal } from './components/TaskEditModal'
import type { Task } from './types/task'
import './App.css'

const INSIGHTS_PATH = '/insights'

const getTaskIdFromPath = (pathname: string): number | null => {
  const match = pathname.match(/^\/tasks\/(\d+)$/)
  return match ? Number(match[1]) : null
}

function App() {
  const { tasks, isLoading, error, toast, createTask, updateTask, completeTask, deleteTask, refetch } = useTasks()
  const { dashboard, recentCompletions, isLoading: isInsightsLoading, error: insightsError, refetch: refetchInsights } = useInsights()
  const [showForm, setShowForm] = useState(false)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [pathname, setPathname] = useState(window.location.pathname)

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

  const refreshAll = async () => {
    await Promise.all([refetch(), refetchInsights()])
  }

  const handleCreateTask = async (req: Parameters<typeof createTask>[0]) => {
    const ok = await createTask(req)
    if (ok) {
      await refetchInsights()
      setShowForm(false)
    }
    return ok
  }

  const handleUpdateTask = async (id: number, request: Parameters<typeof updateTask>[1]) => {
    const ok = await updateTask(id, request)
    if (ok) await refreshAll()
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
    const ok = await completeTask(id)
    if (ok) await refreshAll()
    return ok
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
        {!selectedTaskId && !isInsightsPage && showForm && (
          <TaskForm
            onSubmit={handleCreateTask}
            onCancel={() => setShowForm(false)}
          />
        )}

        {error && <p className="app-error">{error}</p>}
        {toast && <p className="app-toast">{toast}</p>}

        {selectedTaskId ? (
          <TaskDetailPage
            taskId={selectedTaskId}
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
                <h2>현재 Task</h2>
              </div>
              <button
                type="button"
                className={`btn-secondary section-header__task-toggle ${showForm ? 'section-header__task-toggle--active' : ''}`}
                onClick={() => setShowForm((v) => !v)}
              >
                {showForm ? '닫기' : '+ 새 Task'}
              </button>
            </div>
            <TaskSection
              tasks={tasks}
              onComplete={handleCompleteTask}
              onEdit={setSelectedTask}
              onView={(task) => navigateTo(`/tasks/${task.id}`)}
            />
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
    </div>
  )
}

export default App
