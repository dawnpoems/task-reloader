export const HOME_PATH = '/'
export const INSIGHTS_PATH = '/insights'
export const ALERT_SETTINGS_PATH = '/alerts'
export const LOGIN_PATH = '/auth/login'
export const SIGNUP_PATH = '/auth/signup'
export const ADMIN_APPROVALS_PATH = '/admin/approvals'

const POST_LOGIN_REDIRECT_KEY = 'task_reloader.post_login_redirect'

export const getTaskIdFromPath = (pathname: string): number | null => {
  const match = pathname.match(/^\/tasks\/(\d+)$/)
  return match ? Number(match[1]) : null
}

export const isPublicPath = (pathname: string): boolean => pathname === LOGIN_PATH || pathname === SIGNUP_PATH

export const canAccessPathByRole = (pathname: string, role?: 'USER' | 'ADMIN'): boolean => {
  if (pathname === ADMIN_APPROVALS_PATH) return role === 'ADMIN'
  return true
}

export const isKnownPath = (pathname: string): boolean => {
  if (pathname === HOME_PATH) return true
  if (pathname === INSIGHTS_PATH) return true
  if (pathname === ALERT_SETTINGS_PATH) return true
  if (pathname === LOGIN_PATH) return true
  if (pathname === SIGNUP_PATH) return true
  if (pathname === ADMIN_APPROVALS_PATH) return true
  return getTaskIdFromPath(pathname) !== null
}

export const savePostLoginRedirect = (pathname: string): void => {
  if (!isKnownPath(pathname) || isPublicPath(pathname)) return
  window.sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, pathname)
}

export const popPostLoginRedirect = (): string | null => {
  const stored = window.sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY)
  if (!stored) return null
  window.sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
  return stored
}

export const clearPostLoginRedirect = (): void => {
  window.sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
}

export const loginPathWithEmailPrefill = (): string => {
  const email = new URLSearchParams(window.location.search).get('email')?.trim()
  if (!email) return LOGIN_PATH
  return `${LOGIN_PATH}?email=${encodeURIComponent(email)}`
}
