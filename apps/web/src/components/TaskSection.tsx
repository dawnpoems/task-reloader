import type { Task } from '../types/task'
import { formatDate } from '../lib/utils'

interface TaskSectionProps {
  tasks: Task[]
  onComplete: (id: number) => Promise<boolean>
  onDelete: (id: number) => Promise<boolean>
}

const STATUS_LABEL: Record<string, string> = {
  TODAY: '오늘',
  UPCOMING: '예정',
  OVERDUE: '기한 초과',
  COMPLETED: '완료',
  PENDING: '대기',
}

const STATUS_CLASS: Record<string, string> = {
  TODAY: 'task-card--today',
  UPCOMING: 'task-card--upcoming',
  OVERDUE: 'task-card--overdue',
  COMPLETED: 'task-card--completed',
  PENDING: 'task-card--pending',
}

export function TaskSection({ tasks, onComplete, onDelete }: TaskSectionProps) {
  if (tasks.length === 0) {
    return <p className="task-section__empty">등록된 Task가 없습니다.</p>
  }

  return (
    <ul className="task-section">
      {tasks.map((task) => (
        <li key={task.id} className={`task-card ${STATUS_CLASS[task.status] ?? ''}`}>
          <div className="task-card__header">
            <span className="task-card__status">{STATUS_LABEL[task.status] ?? task.status}</span>
            <span className="task-card__due">다음 예정: {formatDate(task.nextDueAt)}</span>
          </div>
          <h3 className="task-card__title">{task.name}</h3>
          <p className="task-card__description">매 {task.everyNDays}일마다 반복</p>
          <div className="task-card__footer">
            <span className="task-card__date">생성: {formatDate(task.createdAt)}</span>
            <div className="task-card__actions">
              {task.status !== 'COMPLETED' && task.isActive && (
                <button onClick={() => onComplete(task.id)} className="btn-complete">완료</button>
              )}
              <button onClick={() => onDelete(task.id)} className="btn-delete">삭제</button>
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}

