import { useCallback, useEffect, useState } from 'react'
import type { CreateTaskRequest, UpdateTaskRequest } from '../types/task'

interface UseTaskWorkflowOptions {
  isDataEnabled: boolean
  selectedTaskId: number | null
  isUpcomingLoaded: boolean
  refetchDueNow: () => Promise<void>
  refetchInsights: () => Promise<void>
  refetchDashboard: () => Promise<void>
  fetchUpcomingTasks: () => Promise<void>
  createTask: (request: CreateTaskRequest) => Promise<boolean>
  updateTask: (id: number, request: UpdateTaskRequest) => Promise<boolean>
  completeTask: (id: number) => Promise<boolean>
  deleteTask: (id: number) => Promise<boolean>
  onCreateSuccess: () => void
  onSelectedTaskDeleted: () => void
}

export function useTaskWorkflow({
  isDataEnabled,
  selectedTaskId,
  isUpcomingLoaded,
  refetchDueNow,
  refetchInsights,
  refetchDashboard,
  fetchUpcomingTasks,
  createTask,
  updateTask,
  completeTask,
  deleteTask,
  onCreateSuccess,
  onSelectedTaskDeleted,
}: UseTaskWorkflowOptions) {
  const [completingTaskIds, setCompletingTaskIds] = useState<Set<number>>(new Set())
  const [completedTaskIds, setCompletedTaskIds] = useState<Set<number>>(new Set())
  const [detailRefreshToken, setDetailRefreshToken] = useState(0)

  useEffect(() => {
    if (isDataEnabled) return

    setCompletingTaskIds(new Set())
    setCompletedTaskIds(new Set())
  }, [isDataEnabled])

  const refreshAll = useCallback(async () => {
    if (!isDataEnabled) return

    const tasksToRefresh = [refetchDueNow(), refetchInsights(), refetchDashboard()]
    if (isUpcomingLoaded) {
      tasksToRefresh.push(fetchUpcomingTasks())
    }
    await Promise.all(tasksToRefresh)
  }, [fetchUpcomingTasks, isDataEnabled, isUpcomingLoaded, refetchDashboard, refetchDueNow, refetchInsights])

  const handleCreateTask = useCallback(async (request: CreateTaskRequest) => {
    const ok = await createTask(request)
    if (ok) {
      await refreshAll()
      onCreateSuccess()
    }
    return ok
  }, [createTask, onCreateSuccess, refreshAll])

  const handleUpdateTask = useCallback(async (id: number, request: UpdateTaskRequest) => {
    const ok = await updateTask(id, request)
    if (ok) {
      await refreshAll()
      setDetailRefreshToken((prev) => prev + 1)
    }
    return ok
  }, [refreshAll, updateTask])

  const handleDeleteTask = useCallback(async (id: number) => {
    const ok = await deleteTask(id)
    if (ok) {
      await refreshAll()
      if (selectedTaskId === id) onSelectedTaskDeleted()
    }
    return ok
  }, [deleteTask, onSelectedTaskDeleted, refreshAll, selectedTaskId])

  const handleCompleteTask = useCallback(async (id: number) => {
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
  }, [completeTask, refreshAll])

  const handleCompleteTaskFromDetail = useCallback(async (id: number) => {
    const ok = await completeTask(id)
    if (!ok) return false

    const tasksToRefresh = [refetchDueNow(), refetchDashboard()]
    if (isUpcomingLoaded) {
      tasksToRefresh.push(fetchUpcomingTasks())
    }
    await Promise.all(tasksToRefresh)
    return true
  }, [completeTask, fetchUpcomingTasks, isUpcomingLoaded, refetchDashboard, refetchDueNow])

  return {
    completingTaskIds,
    completedTaskIds,
    detailRefreshToken,
    refreshAll,
    handleCreateTask,
    handleUpdateTask,
    handleDeleteTask,
    handleCompleteTask,
    handleCompleteTaskFromDetail,
  }
}
