import { ErrorNotice } from './ErrorNotice'
import { TaskSection } from './TaskSection'
import type { DashboardSummary } from '../types/insights'
import type { Task } from '../types/task'

interface HomePageProps {
  dashboard: DashboardSummary | null
  dueNowTasks: Task[]
  dueNowError: string | null
  isLoading: boolean
  showForm: boolean
  createTaskButtonRef: React.RefObject<HTMLButtonElement>
  upcomingTasks: Task[]
  isUpcomingOpen: boolean
  isUpcomingLoading: boolean
  upcomingError: string | null
  completingTaskIds: Set<number>
  completedTaskIds: Set<number>
  onCreateTaskClick: () => void
  onToggleUpcoming: () => void
  onRetryUpcoming: () => void
  onCompleteTask: (id: number) => Promise<boolean>
  onEditTask: (task: Task) => void
  onViewTask: (task: Task) => void
}

export function HomePage({
  dashboard,
  dueNowTasks,
  dueNowError,
  isLoading,
  showForm,
  createTaskButtonRef,
  upcomingTasks,
  isUpcomingOpen,
  isUpcomingLoading,
  upcomingError,
  completingTaskIds,
  completedTaskIds,
  onCreateTaskClick,
  onToggleUpcoming,
  onRetryUpcoming,
  onCompleteTask,
  onEditTask,
  onViewTask,
}: HomePageProps) {
  if (isLoading) {
    return <p className="app-loading">불러오는 중...</p>
  }

  return (
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
            onClick={onCreateTaskClick}
          >
            + 새 Task
          </button>
        )}
      </div>
      {dueNowError ? null : dueNowTasks.length === 0 ? (
        <p className="today-all-done">오늘 할일을 모두 마쳤어요!</p>
      ) : (
        <TaskSection
          tasks={dueNowTasks}
          onComplete={onCompleteTask}
          onEdit={onEditTask}
          onView={onViewTask}
          completingTaskIds={completingTaskIds}
          completedTaskIds={completedTaskIds}
        />
      )}

      <div className="section-collapse">
        <button
          type="button"
          className="btn-secondary section-collapse__trigger"
          onClick={onToggleUpcoming}
        >
          {isUpcomingOpen ? '남은 일정 접기' : `남은 일정 펼치기${dashboard ? ` (${dashboard.upcomingTasks})` : ''}`}
        </button>

        {isUpcomingOpen && (
          <div className="section-collapse__content">
            {upcomingError && <ErrorNotice message={upcomingError} onRetry={onRetryUpcoming} />}
            {isUpcomingLoading ? (
              <p className="app-loading">남은 일정을 불러오는 중...</p>
            ) : (
              <TaskSection
                tasks={upcomingTasks}
                onComplete={onCompleteTask}
                onEdit={onEditTask}
                onView={onViewTask}
                completingTaskIds={completingTaskIds}
                completedTaskIds={completedTaskIds}
              />
            )}
          </div>
        )}
      </div>
    </section>
  )
}
