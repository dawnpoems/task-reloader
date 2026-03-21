import type { Task, TaskStatus } from '../types/task'
import { formatDate } from '../lib/utils'

interface TaskSectionProps {
  tasks: Task[]
  onComplete: (id: number) => Promise<boolean>
  onEdit: (task: Task) => void
  onView: (task: Task) => void
}

const STATUS_LABEL: Record<TaskStatus, string> = {
  TODAY: '오늘 할 일',
  UPCOMING: '예정',
  OVERDUE: '기한 초과',
}

const STATUS_CLASS: Record<TaskStatus, string> = {
  TODAY: 'task-card--today',
  UPCOMING: 'task-card--upcoming',
  OVERDUE: 'task-card--overdue',
}

export function TaskSection({ tasks, onComplete, onEdit, onView }: TaskSectionProps) {
  if (tasks.length === 0) {
    return <p className="task-section__empty">등록된 Task가 없습니다.</p>
  }

  return (
    <ul className="task-section">
      {tasks.map((task) => (
        <li key={task.id} className={`task-card ${STATUS_CLASS[task.status] ?? ''}`}>
          <div className="task-card__layout">
            <div className="task-card__content">
              {/* 상단: 상태 배지 */}
              <div className="task-card__top">
                <span className="task-card__status-badge">{STATUS_LABEL[task.status] ?? task.status}</span>
              </div>

              {/* 메인: Task 이름 */}
              <h3 className="task-card__title">{task.name}</h3>

              {/* 다음 예정일 - 눈에 띄게 */}
              <div className="task-card__due">
                <span className="task-card__due-label">다음 예정</span>
                <span className="task-card__due-date">{formatDate(task.nextDueAt)}</span>
              </div>

              {/* 부가 정보 - 구석에 작게 */}
              <div className="task-card__meta">
                <span>매 {task.everyNDays}일마다</span>
                {task.startDate && <span>시작 {task.startDate}</span>}
                <span>생성 {formatDate(task.createdAt)}</span>
              </div>
            </div>

            <div className="task-card__actions-panel">
              {task.isActive && (
                <button onClick={() => onComplete(task.id)} className="btn-complete">완료</button>
              )}
              <div className="task-card__utility-actions">
                <button onClick={() => onView(task)} className="btn-secondary btn-detail">상세</button>
                <button onClick={() => onEdit(task)} className="btn-edit">수정</button>
              </div>
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
