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
