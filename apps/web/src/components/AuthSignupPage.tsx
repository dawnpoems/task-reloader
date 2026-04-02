import { useState } from 'react'
import type { SignupRequest } from '../types/auth'

interface AuthActionResult {
  success: boolean
  code?: string
  message?: string
}

interface AuthSignupPageProps {
  onSignup: (request: SignupRequest) => Promise<AuthActionResult>
  onGoLogin: () => void
}

export function AuthSignupPage({ onSignup, onGoLogin }: AuthSignupPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isEmailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
  const isPasswordLongEnough = password.length >= 8
  const hasLetter = /[A-Za-z]/.test(password)
  const hasNumber = /\d/.test(password)
  const isPasswordRuleValid = isPasswordLongEnough && hasLetter && hasNumber
  const doesConfirmMatch = confirmPassword.length > 0 ? password === confirmPassword : true

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setSuccessMessage(null)

    const normalizedEmail = email.trim()
    if (!normalizedEmail) {
      setError('이메일을 입력해 주세요.')
      return
    }
    if (!isEmailValid) {
      setError('유효한 이메일 형식이 아닙니다.')
      return
    }
    if (!isPasswordRuleValid) {
      setError('비밀번호는 8자 이상, 영문/숫자를 모두 포함해야 합니다.')
      return
    }

    if (password !== confirmPassword) {
      setError('비밀번호 확인이 일치하지 않습니다.')
      return
    }

    setIsSubmitting(true)
    try {
      const result = await onSignup({ email: normalizedEmail, password })
      if (!result.success) {
        if (result.code === 'EMAIL_ALREADY_EXISTS') {
          setError('이미 사용 중인 이메일입니다. 다른 이메일로 시도해 주세요.')
          return
        }
        setError(result.message ?? '회원가입에 실패했습니다.')
        return
      }

      setSuccessMessage('가입이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다.')
      setPassword('')
      setConfirmPassword('')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card" aria-labelledby="signup-title">
        <h1 id="signup-title">회원가입</h1>
        <p className="auth-card__subtitle">새 계정을 등록하고 관리자 승인을 기다리세요.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span>이메일</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
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
              autoComplete="new-password"
              required
            />
            <small className="auth-form__hint">
              8자 이상, 영문/숫자를 모두 포함해야 합니다.
            </small>
            {password && !isPasswordRuleValid && (
              <small className="auth-form__hint auth-form__hint--error">
                비밀번호 규칙을 다시 확인해 주세요.
              </small>
            )}
          </label>

          <label className="auth-form__field">
            <span>비밀번호 확인</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
              required
            />
            {confirmPassword && !doesConfirmMatch && (
              <small className="auth-form__hint auth-form__hint--error">
                비밀번호 확인이 일치하지 않습니다.
              </small>
            )}
          </label>

          {error && <p className="auth-form__error">{error}</p>}
          {successMessage && <p className="auth-form__success">{successMessage}</p>}

          <button type="submit" disabled={isSubmitting || !isEmailValid || !isPasswordRuleValid || !doesConfirmMatch}>
            {isSubmitting ? '가입 중...' : '회원가입'}
          </button>
        </form>

        <div className="auth-card__footer">
          <p>이미 계정이 있나요?</p>
          <button type="button" className="auth-link-button" onClick={onGoLogin}>
            로그인으로 이동
          </button>
        </div>
      </section>
    </div>
  )
}
