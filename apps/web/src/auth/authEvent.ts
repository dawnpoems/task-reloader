const AUTH_EVENT_STORAGE_KEY = 'task_reloader.auth_event'

type AuthEventType = 'LOGOUT'

interface AuthEventPayload {
  type: AuthEventType
  at: number
}

function isAuthEventType(value: unknown): value is AuthEventType {
  return value === 'LOGOUT'
}

export function publishAuthEvent(type: AuthEventType): void {
  const payload: AuthEventPayload = {
    type,
    at: Date.now(),
  }

  window.localStorage.setItem(AUTH_EVENT_STORAGE_KEY, JSON.stringify(payload))
}

export function subscribeAuthEvent(onEvent: (type: AuthEventType) => void): () => void {
  const handleStorage = (event: StorageEvent) => {
    if (event.key !== AUTH_EVENT_STORAGE_KEY) return
    if (!event.newValue) return

    try {
      const payload = JSON.parse(event.newValue) as Partial<AuthEventPayload>
      if (!isAuthEventType(payload.type)) return
      onEvent(payload.type)
    } catch {
      // ignore malformed payload
    }
  }

  window.addEventListener('storage', handleStorage)
  return () => {
    window.removeEventListener('storage', handleStorage)
  }
}
