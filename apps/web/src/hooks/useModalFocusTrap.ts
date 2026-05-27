import { useCallback, useLayoutEffect, useRef } from 'react'
import type { KeyboardEvent, MutableRefObject, RefObject } from 'react'

interface UseModalFocusTrapOptions {
  isOpen?: boolean
  isCloseDisabled?: boolean
  onRequestClose: () => void
  initialFocusRef?: RefObject<HTMLElement>
  initialFocusSelector?: string
  restoreFocusRef?: MutableRefObject<HTMLElement | null>
}

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

export function useModalFocusTrap<T extends HTMLElement>({
  isOpen = true,
  isCloseDisabled = false,
  onRequestClose,
  initialFocusRef,
  initialFocusSelector,
  restoreFocusRef,
}: UseModalFocusTrapOptions) {
  const modalRef = useRef<T | null>(null)
  const defaultRestoreFocusRef = useRef<HTMLElement | null>(
    document.activeElement instanceof HTMLElement ? document.activeElement : null
  )
  const previousFocusedElementRef = restoreFocusRef ?? defaultRestoreFocusRef

  useLayoutEffect(() => {
    if (!isOpen) return

    const previousFocusTarget = previousFocusedElementRef.current
    const focusInitialTarget = () => {
      const modalEl = modalRef.current
      if (!modalEl) return

      const selectedTarget = initialFocusSelector
        ? modalEl.querySelector<HTMLElement>(initialFocusSelector)
        : null
      const initialTarget = initialFocusRef?.current ?? selectedTarget ?? modalEl
      initialTarget.focus()
    }

    const rafId = window.requestAnimationFrame(focusInitialTarget)
    const timeoutId = window.setTimeout(focusInitialTarget, 60)

    return () => {
      window.cancelAnimationFrame(rafId)
      window.clearTimeout(timeoutId)
      if (!previousFocusTarget) return
      window.requestAnimationFrame(() => {
        previousFocusTarget.focus()
      })
    }
  }, [initialFocusRef, initialFocusSelector, isOpen, previousFocusedElementRef])

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<T>) => {
      if (e.key === 'Escape') {
        if (isCloseDisabled) return
        e.preventDefault()
        onRequestClose()
        return
      }

      if (e.key !== 'Tab') return

      const modalEl = modalRef.current
      if (!modalEl) return

      const focusables = Array.from(
        modalEl.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
      ).filter((el) => !el.hasAttribute('disabled') && el.tabIndex >= 0)

      if (focusables.length === 0) return

      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      const active = document.activeElement as HTMLElement | null

      if (e.shiftKey) {
        if (active === first || !modalEl.contains(active)) {
          e.preventDefault()
          last.focus()
        }
        return
      }

      if (active === last) {
        e.preventDefault()
        first.focus()
      }
    },
    [isCloseDisabled, onRequestClose]
  )

  return { modalRef, handleKeyDown }
}
