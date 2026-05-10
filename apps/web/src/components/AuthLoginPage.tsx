import { useState } from 'react'
import { useRetryAfterCountdown } from '../hooks/useRetryAfterCountdown'
import type { LoginRequest } from '../types/auth'

const DEMO_ACCOUNT_EMAIL = 'demo@dawnpoem.kr'
const DEMO_ACCOUNT_PASSWORD = 'demo1234!'

interface AuthActionResult {
  success: boolean
  code?: string
  message?: string
  retryAfterSeconds?: number
}

interface AuthLoginPageProps {
  onLogin: (request: LoginRequest) => Promise<AuthActionResult>
  onGoSignup: () => void
  noticeMessage?: string | null
  onDismissNotice?: () => void
}

export function AuthLoginPage({ onLogin, onGoSignup, noticeMessage, onDismissNotice }: AuthLoginPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isDemoAccountOpen, setIsDemoAccountOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [accountStateNotice, setAccountStateNotice] = useState<{ tone: 'pending' | 'rejected'; message: string } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const { isActive: isRateLimited, remainingSeconds, startCountdown, clearCountdown } = useRetryAfterCountdown()

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isRateLimited) return

    const normalizedEmail = email.trim()
    if (!normalizedEmail) {
      setError('이메일을 입력해 주세요.')
      return
    }
    if (!password) {
      setError('비밀번호를 입력해 주세요.')
      return
    }

    setError(null)
    setAccountStateNotice(null)
    setIsSubmitting(true)

    try {
      const result = await onLogin({ email: normalizedEmail, password })
      if (!result.success) {
        const retryAfterSeconds = result.retryAfterSeconds
        if (typeof retryAfterSeconds === 'number' && retryAfterSeconds > 0) {
          startCountdown(retryAfterSeconds)
        } else {
          clearCountdown()
        }

        if (result.code === 'ACCOUNT_PENDING') {
          setAccountStateNotice({
            tone: 'pending',
            message: '계정이 관리자 승인 대기 상태입니다. 승인 완료 후 다시 로그인해 주세요.',
          })
        } else if (result.code === 'ACCOUNT_REJECTED') {
          setAccountStateNotice({
            tone: 'rejected',
            message: '승인이 거절된 계정입니다. 관리자에게 문의하거나 다른 이메일로 다시 가입해 주세요.',
          })
        }
        setError(result.message ?? '로그인에 실패했습니다. 이메일/비밀번호를 확인한 뒤 다시 시도해 주세요.')
        return
      }

      clearCountdown()
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card" aria-labelledby="login-title">
        <h1 id="login-title">로그인</h1>
        <p className="auth-card__subtitle">승인된 계정으로 Task Reloader를 시작하세요.</p>

        {noticeMessage && (
          <div className="auth-form__notice" role="status" aria-live="polite">
            <p>{noticeMessage}</p>
            {onDismissNotice && (
              <button type="button" className="auth-link-button" onClick={onDismissNotice}>
                닫기
              </button>
            )}
          </div>
        )}

        <button
          type="button"
          className="auth-demo-toggle"
          aria-expanded={isDemoAccountOpen}
          aria-controls="demo-account-panel"
          onClick={() => setIsDemoAccountOpen((prev) => !prev)}
        >
          {isDemoAccountOpen ? '데모 계정 숨기기' : '데모 계정으로 빠르게 체험하기'}
        </button>
        {isDemoAccountOpen && (
          <div id="demo-account-panel" className="auth-demo-panel" role="region" aria-live="polite">
            <p className="auth-demo-panel__title">데모 계정 안내</p>
            <p>
              <strong>이메일:</strong> {DEMO_ACCOUNT_EMAIL}
            </p>
            <p>
              <strong>비밀번호:</strong> {DEMO_ACCOUNT_PASSWORD}
            </p>
            <p className="auth-demo-panel__note">공용 계정이라 데이터가 수시로 바뀔 수 있습니다.</p>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span>이메일</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoFocus
              autoComplete="email"
              required
            />
          </label>

          <label className="auth-form__field">
            <span>비밀번호</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </label>

          {error && <p className="auth-form__error">{error}</p>}
          {accountStateNotice && (
            <div className={`auth-state-notice auth-state-notice--${accountStateNotice.tone}`} role="status" aria-live="polite">
              <p>{accountStateNotice.message}</p>
            </div>
          )}
          {isRateLimited && (
            <div className="auth-state-notice auth-state-notice--throttled" role="status" aria-live="polite">
              <p>요청이 많아 잠시 제한되었습니다. {remainingSeconds}초 후 다시 시도해 주세요.</p>
            </div>
          )}

          <button type="submit" disabled={isSubmitting || isRateLimited}>
            {isSubmitting ? '로그인 중...' : isRateLimited ? `${remainingSeconds}초 후 재시도` : '로그인'}
          </button>
        </form>

        <div className="auth-card__footer">
          <p>계정이 없나요?</p>
          <button type="button" className="auth-link-button" onClick={onGoSignup} disabled={isSubmitting}>
            회원가입
          </button>
        </div>
      </section>
    </div>
  )
}
