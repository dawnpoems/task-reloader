export const AUTH_NOTICE_STORAGE_KEY = 'task_reloader.auth_notice'

export type AuthNoticeCode = 'SESSION_EXPIRED'

export const AUTH_NOTICE_SESSION_EXPIRED: AuthNoticeCode = 'SESSION_EXPIRED'

export function pushAuthNotice(code: AuthNoticeCode): void {
  window.sessionStorage.setItem(AUTH_NOTICE_STORAGE_KEY, code)
}

export function popAuthNotice(): AuthNoticeCode | null {
  const raw = window.sessionStorage.getItem(AUTH_NOTICE_STORAGE_KEY)
  if (!raw) return null
  window.sessionStorage.removeItem(AUTH_NOTICE_STORAGE_KEY)
  return raw === AUTH_NOTICE_SESSION_EXPIRED ? AUTH_NOTICE_SESSION_EXPIRED : null
}

export function getAuthNoticeMessage(code: AuthNoticeCode): string {
  if (code === AUTH_NOTICE_SESSION_EXPIRED) {
    return '세션이 만료되었거나 다른 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.'
  }
  return '다시 로그인해 주세요.'
}
