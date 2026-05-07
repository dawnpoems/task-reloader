import { useCallback, useEffect, useMemo, useState } from 'react'

interface RetryAfterCountdownState {
  isActive: boolean
  remainingSeconds: number
  startCountdown: (seconds: number) => void
  clearCountdown: () => void
}

export function useRetryAfterCountdown(): RetryAfterCountdownState {
  const [deadlineTimestamp, setDeadlineTimestamp] = useState<number | null>(null)
  const [remainingSeconds, setRemainingSeconds] = useState(0)

  const clearCountdown = useCallback(() => {
    setDeadlineTimestamp(null)
    setRemainingSeconds(0)
  }, [])

  const startCountdown = useCallback(
    (seconds: number) => {
      if (!Number.isFinite(seconds) || seconds <= 0) {
        clearCountdown()
        return
      }

      const normalizedSeconds = Math.ceil(seconds)
      setDeadlineTimestamp(Date.now() + normalizedSeconds * 1000)
      setRemainingSeconds(normalizedSeconds)
    },
    [clearCountdown]
  )

  useEffect(() => {
    if (deadlineTimestamp === null) return

    const tick = () => {
      const nextRemaining = Math.max(0, Math.ceil((deadlineTimestamp - Date.now()) / 1000))
      setRemainingSeconds(nextRemaining)
      if (nextRemaining === 0) {
        setDeadlineTimestamp(null)
      }
    }

    tick()
    const timerId = window.setInterval(tick, 250)
    return () => window.clearInterval(timerId)
  }, [deadlineTimestamp])

  return useMemo(
    () => ({
      isActive: deadlineTimestamp !== null && remainingSeconds > 0,
      remainingSeconds,
      startCountdown,
      clearCountdown,
    }),
    [deadlineTimestamp, remainingSeconds, startCountdown, clearCountdown]
  )
}
