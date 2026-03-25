export interface DashboardSummary {
  totalTasks: number
  overdueTasks: number
  todayTasks: number
  upcomingTasks: number
  completedToday: number
  completedLast7Days: number
}

export interface TaskTrendInsight {
  taskId: number
  taskName: string
  completionCount: number
  delayedCount: number
  delayRatePct: number
}

export type RiskyTaskReason = 'OVERDUE_7D_PLUS' | 'NO_COMPLETION_30D'

export interface RiskyTaskInsight {
  taskId: number
  taskName: string
  nextDueAt: string
  lastCompletedAt: string | null
  reasons: RiskyTaskReason[]
}

export interface InsightsOverview {
  periodDays: number
  periodStart: string
  periodEnd: string
  timezone: string
  activeTaskCount: number
  completedTaskCount: number
  completionCount: number
  delayedCompletionCount: number
  completionRatePct: number
  delayRatePct: number
  averageDelayMinutes: number
  riskyTaskCount: number
  riskyTasks: RiskyTaskInsight[]
  taskTrends: TaskTrendInsight[]
}

export interface RecentTaskCompletion {
  id: number
  taskId: number
  taskName: string
  completedAt: string
  previousDueAt: string
  nextDueAt: string
}
