import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { authApi } from '../api/auth'
import { clearAccessToken, configureAuthClient, extractErrorCode, extractErrorMessage, setAccessToken } from '../api/client'
import type { AuthUser, LoginRequest, LoginResponse, MeResponse, SignupRequest } from '../types/auth'

interface AuthActionResult {
  success: boolean
  code?: string
  message?: string
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isInitializing: boolean
  login: (request: LoginRequest) => Promise<AuthActionResult>
  signup: (request: SignupRequest) => Promise<AuthActionResult>
  logout: () => Promise<void>
  refreshSession: () => Promise<boolean>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function toAuthUser(payload: LoginResponse | MeResponse): AuthUser {
  return {
    userId: payload.userId,
    email: payload.email,
    role: payload.role,
    status: payload.status,
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isInitializing, setIsInitializing] = useState(true)
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null)

  const refreshAccessToken = useCallback(async (): Promise<string | null> => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current

    refreshPromiseRef.current = (async () => {
      const res = await authApi.refresh()
      if (res.success && res.data?.accessToken) {
        setAccessToken(res.data.accessToken)
        return res.data.accessToken
      }
      clearAccessToken()
      return null
    })().finally(() => {
      refreshPromiseRef.current = null
    })

    return refreshPromiseRef.current
  }, [])

  const loadMe = useCallback(async (): Promise<AuthUser | null> => {
    const res = await authApi.me()
    if (res.success && res.data) {
      const nextUser = toAuthUser(res.data)
      setUser(nextUser)
      return nextUser
    }
    setUser(null)
    return null
  }, [])

  const refreshSession = useCallback(async (): Promise<boolean> => {
    const token = await refreshAccessToken()
    if (!token) {
      setUser(null)
      return false
    }
    const me = await loadMe()
    return me !== null
  }, [loadMe, refreshAccessToken])

  useEffect(() => {
    configureAuthClient({
      refreshAccessToken,
      onAuthFailure: () => {
        clearAccessToken()
        setUser(null)
      },
    })
  }, [refreshAccessToken])

  useEffect(() => {
    let cancelled = false

    const initialize = async () => {
      const token = await refreshAccessToken()
      if (cancelled) return

      if (token) {
        await loadMe()
      } else {
        setUser(null)
      }

      if (!cancelled) {
        setIsInitializing(false)
      }
    }

    initialize()

    return () => {
      cancelled = true
    }
  }, [loadMe, refreshAccessToken])

  const login = useCallback(async (request: LoginRequest): Promise<AuthActionResult> => {
    const res = await authApi.login(request)
    if (!res.success || !res.data) {
      return {
        success: false,
        code: extractErrorCode(res.error),
        message: extractErrorMessage(res.error, '로그인에 실패했습니다.'),
      }
    }

    setAccessToken(res.data.accessToken)
    setUser(toAuthUser(res.data))
    return { success: true }
  }, [])

  const signup = useCallback(async (request: SignupRequest): Promise<AuthActionResult> => {
    const res = await authApi.signup(request)
    if (res.success) {
      return { success: true }
    }
    return {
      success: false,
      code: extractErrorCode(res.error),
      message: extractErrorMessage(res.error, '회원가입에 실패했습니다.'),
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      clearAccessToken()
      setUser(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isInitializing,
      login,
      signup,
      logout,
      refreshSession,
    }),
    [isInitializing, login, logout, refreshSession, signup, user]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
