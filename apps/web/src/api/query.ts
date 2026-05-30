type QueryValue = string | number | boolean | null | undefined

export function withQuery(endpoint: string, query: Record<string, QueryValue>): string {
  const searchParams = new URLSearchParams()

  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined) continue
    searchParams.set(key, String(value))
  }

  const queryString = searchParams.toString()
  return queryString ? `${endpoint}?${queryString}` : endpoint
}
