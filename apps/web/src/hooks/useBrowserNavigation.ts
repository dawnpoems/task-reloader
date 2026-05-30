import { useCallback, useEffect, useState } from 'react'

export function useBrowserNavigation() {
  const [pathname, setPathname] = useState(window.location.pathname)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigateTo = useCallback((nextPath: string) => {
    if (nextPath === window.location.pathname) {
      setPathname(nextPath)
      return
    }
    window.history.pushState({}, '', nextPath)
    setPathname(window.location.pathname)
  }, [])

  const replaceTo = useCallback((nextPath: string) => {
    if (nextPath === window.location.pathname) {
      setPathname(nextPath)
      return
    }
    window.history.replaceState({}, '', nextPath)
    setPathname(window.location.pathname)
  }, [])

  return { pathname, navigateTo, replaceTo }
}
