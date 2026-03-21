import { useState } from 'react'
import type { CreateTaskRequest } from '../types/task'

interface TaskFormProps {
  onSubmit: (request: CreateTaskRequest) => Promise<boolean>
  onCancel?: () => void
  hideTitle?: boolean
}

const todayDateInput = (): string => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function TaskForm({ onSubmit, onCancel, hideTitle = false }: TaskFormProps) {
  const [name, setName] = useState('')
  const [everyNDays, setEveryNDays] = useState(7)
  const [startDate, setStartDate] = useState(todayDateInput())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) { setError('이름을 입력해 주세요.'); return }
    if (everyNDays < 1) { setError('반복 주기는 1일 이상이어야 합니다.'); return }
    setIsSubmitting(true)
    setError(null)
    const success = await onSubmit({ name: name.trim(), everyNDays, startDate: startDate || undefined })
    if (success) { setName(''); setEveryNDays(7); setStartDate(todayDateInput()) }
    else { setError('저장에 실패했습니다. 다시 시도해 주세요.') }
    setIsSubmitting(false)
  }

  return (
    <form onSubmit={handleSubmit} className="task-form">
      {!hideTitle && <h2>새 Task 추가</h2>}
      {error && <p className="task-form__error" role="alert" aria-live="assertive">{error}</p>}

      <div className="task-form__field">
        <label htmlFor="name">이름 *</label>
        <input id="name" type="text" value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Task 이름을 입력하세요" disabled={isSubmitting} />
      </div>

      <div className="task-form__field">
        <label htmlFor="everyNDays">반복 주기 (일) *</label>
        <input id="everyNDays" type="number" min={1} value={everyNDays}
          onChange={(e) => setEveryNDays(Number(e.target.value))}
          disabled={isSubmitting} />
      </div>

      <div className="task-form__field">
        <label htmlFor="startDate">시작 날짜</label>
        <input
          id="startDate"
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          disabled={isSubmitting}
        />
      </div>

      <div className="task-form__actions">
        {onCancel && (
          <button type="button" onClick={onCancel} disabled={isSubmitting} className="btn-secondary">
            취소
          </button>
        )}
        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? '저장 중...' : 'Task 추가'}
        </button>
      </div>
    </form>
  )
}
