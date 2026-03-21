interface ErrorNoticeProps {
  message: string
  retryLabel?: string
  onRetry?: () => void
}

export function ErrorNotice({ message, retryLabel = '다시 시도', onRetry }: ErrorNoticeProps) {
  return (
    <div className="app-error" role="alert" aria-live="assertive">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn-secondary" onClick={onRetry}>
          {retryLabel}
        </button>
      )}
    </div>
  )
}
