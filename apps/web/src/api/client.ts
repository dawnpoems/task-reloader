// 개발: vite proxy(/api → localhost:8080), 운영: nginx가 /api → api:8080 프록시
const API_BASE_URL = '/api'

export interface ApiError {
  code: string
  message: string
  requestId?: string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiError | string
}

interface RequestOptions extends RequestInit {
  skipAuth?: boolean
  retryOnUnauthorized?: boolean
}

interface AuthClientConfig {
  refreshAccessToken?: () => Promise<string | null>
  onAuthFailure?: () => void
}

let accessToken: string | null = null
let refreshAccessTokenHandler: (() => Promise<string | null>) | null = null
let authFailureHandler: (() => void) | null = null
let refreshInFlight: Promise<string | null> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export function clearAccessToken(): void {
  accessToken = null
}

export function configureAuthClient(config: AuthClientConfig): void {
  refreshAccessTokenHandler = config.refreshAccessToken ?? null
  authFailureHandler = config.onAuthFailure ?? null
}

async function refreshAccessTokenOnce(): Promise<string | null> {
  if (!refreshAccessTokenHandler) return null
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessTokenHandler()
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

async function executeFetch(endpoint: string, options: RequestInit, skipAuth: boolean): Promise<Response> {
  const headers = new Headers(options.headers ?? {})

  if (options.body !== undefined && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!skipAuth && accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  return fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: options.credentials ?? 'include',
  })
}

async function parseResponse<T>(response: Response): Promise<ApiResponse<T>> {
  if (response.status === 204 || response.status === 205 || response.headers.get('content-length') === '0') {
    return { success: response.ok }
  }

  const text = await response.text()
  if (!text.trim()) {
    if (response.ok) return { success: true }
    return {
      success: false,
      error: { code: 'HTTP_ERROR', message: `요청에 실패했습니다. (HTTP ${response.status})` },
    }
  }

  try {
    const body = JSON.parse(text) as ApiResponse<T>
    return body
  } catch (error) {
    if (!response.ok) {
      return {
        success: false,
        error: { code: 'INVALID_RESPONSE', message: '서버 응답을 해석하지 못했습니다.' },
      }
    }
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error occurred' }
  }
}

async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { skipAuth = false, retryOnUnauthorized = true, ...fetchOptions } = options

  try {
    let response = await executeFetch(endpoint, fetchOptions, skipAuth)

    if (response.status === 401 && !skipAuth && retryOnUnauthorized) {
      const refreshedToken = await refreshAccessTokenOnce()
      if (refreshedToken) {
        response = await executeFetch(endpoint, fetchOptions, false)
      } else {
        authFailureHandler?.()
      }
    }

    return parseResponse<T>(response)
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error occurred' }
  }
}

// error 필드가 객체({ code, message })이면 message를, 문자열이면 그대로 반환
export function extractErrorMessage(
  error: ApiResponse<unknown>['error'],
  fallback = '알 수 없는 오류가 발생했습니다.'
): string {
  if (!error) return fallback
  if (typeof error === 'string') return error
  return error.message || fallback
}

export function extractErrorCode(error: ApiResponse<unknown>['error']): string | undefined {
  if (!error || typeof error === 'string') return undefined
  return error.code
}

export const apiClient = {
  get: <T,>(endpoint: string, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: 'GET' }),
  post: <T,>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T,>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T,>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T,>(endpoint: string, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: 'DELETE' }),
}
