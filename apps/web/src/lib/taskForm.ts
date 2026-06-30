export function normalizeEveryNDaysInput(value: string): string {
  const digitsOnly = value.replace(/\D/g, '')
  return digitsOnly.replace(/^0+(?=\d)/, '')
}

export function parseEveryNDaysInput(value: string): number | null {
  const normalizedValue = normalizeEveryNDaysInput(value)
  if (!normalizedValue) return null

  const everyNDays = Number(normalizedValue)
  if (!Number.isInteger(everyNDays) || everyNDays < 1) return null

  return everyNDays
}
