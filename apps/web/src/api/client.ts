// 개발: vite proxy(/api → localhost:8080), 운영: nginx가 /api → api:8080 프록시
const API_BASE_URL = '/api'

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: { code: string; message: string } | string
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    })
    // 백엔드가 이미 { success, data, error } 구조로 응답
    const body: ApiResponse<T> = await response.json()
    return body
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

export const apiClient = {
  get: <T,>(endpoint: string) =>
    request<T>(endpoint, { method: 'GET' }),
  post: <T,>(endpoint: string, body: unknown) =>
    request<T>(endpoint, { method: 'POST', body: JSON.stringify(body) }),
  put: <T,>(endpoint: string, body: unknown) =>
    request<T>(endpoint, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T,>(endpoint: string, body: unknown) =>
    request<T>(endpoint, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T,>(endpoint: string) =>
    request<T>(endpoint, { method: 'DELETE' }),
}
