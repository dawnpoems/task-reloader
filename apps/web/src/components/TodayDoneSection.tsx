import { formatDate, formatDateTime } from '../lib/utils'
import type { DashboardSummary, RecentTaskCompletion } from '../types/insights'

interface TodayDoneSectionProps {
  dashboard: DashboardSummary | null
  todayCompletions: RecentTaskCompletion[]
  isLoading: boolean
  onOpenTask: (taskId: number) => void
}

const KST_TIME_ZONE = 'Asia/Seoul'

function getKstDateKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: KST_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function isSameKstDate(dateStr: string, dateKey: string): boolean {
  return getKstDateKey(new Date(dateStr)) === dateKey
}

function buildTodaySummary(completedToday: number, scheduledCompleted: number, overdueCleared: number): string {
  if (completedToday === 0) {
    return '아직 오늘 완료한 작업은 없습니다. 완료하면 이곳에 오늘의 결과가 쌓입니다.'
  }

  const parts: string[] = []
  if (scheduledCompleted > 0) {
    parts.push(`오늘 예정 작업 ${scheduledCompleted}개 처리`)
  }
  if (overdueCleared > 0) {
    parts.push(`밀려 있던 작업 ${overdueCleared}개 해소`)
  }

  if (parts.length === 0) {
    return `오늘 완료한 작업 ${completedToday}개를 기록했습니다.`
  }
  return `${parts.join(' · ')}했습니다.`
}

export function TodayDoneSection({
  dashboard,
  todayCompletions,
  isLoading,
  onOpenTask,
}: TodayDoneSectionProps) {
  const todayKey = getKstDateKey(new Date())
  const completedToday = dashboard?.completedToday ?? todayCompletions.length
  const scheduledCompleted = todayCompletions.filter((completion) =>
    isSameKstDate(completion.previousDueAt, todayKey)
  ).length
  const overdueCleared = todayCompletions.filter((completion) =>
    getKstDateKey(new Date(completion.previousDueAt)) < todayKey
  ).length

  return (
    <section className="today-done-section">
      <div className="section-header">
        <div>
          <h2>오늘 한 일</h2>
          <p>목록에서 지나가기 쉬운 오늘의 완료 결과를 먼저 정리합니다.</p>
        </div>
      </div>

      <div className="today-done-hero">
        <div>
          <span className="today-done-hero__label">오늘 완료</span>
          <strong className="today-done-hero__value">{isLoading ? '-' : completedToday}</strong>
        </div>
        <p>{isLoading ? '오늘 완료한 작업을 확인하는 중입니다.' : buildTodaySummary(completedToday, scheduledCompleted, overdueCleared)}</p>
      </div>

      {isLoading ? (
        <p className="section-state">불러오는 중...</p>
      ) : todayCompletions.length === 0 ? (
        <p className="section-state">오늘 완료한 작업이 없습니다.</p>
      ) : (
        <>
          <ul className="today-done-list">
            {todayCompletions.map((completion) => (
              <li key={completion.id} className="today-done-item">
                <div>
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => onOpenTask(completion.taskId)}
                  >
                    {completion.taskName}
                  </button>
                  <span>{formatDateTime(completion.completedAt)}</span>
                </div>
                <small>
                  이전 예정 {formatDateTime(completion.previousDueAt)} · 다음 예정 {formatDate(completion.nextDueAt)}
                </small>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
