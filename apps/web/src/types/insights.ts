export interface DashboardSummary {
  totalTasks: number
  overdueTasks: number
  todayTasks: number
  upcomingTasks: number
  completedToday: number
  completedLast7Days: number
}

export interface RecentTaskCompletion {
  id: number
  taskId: number
  taskName: string
  completedAt: string
  previousDueAt: string
  nextDueAt: string
}
