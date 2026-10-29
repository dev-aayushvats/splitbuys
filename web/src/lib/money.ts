/** Formats integer paise as ₹ with Indian digit grouping, e.g. 12345678 -> ₹1,23,456.78 */
export function formatPaise(paise: number): string {
  const sign = paise < 0 ? '-' : ''
  const abs = Math.abs(paise)
  const rupees = String(Math.floor(abs / 100))
  const fraction = String(abs % 100).padStart(2, '0')
  return `${sign}₹${indianGrouping(rupees)}.${fraction}`
}

function indianGrouping(digits: string): string {
  if (digits.length <= 3) return digits
  const head = digits.slice(0, -3)
  const tail = digits.slice(-3)
  const groups: string[] = []
  for (let i = head.length; i > 0; i -= 2) groups.unshift(head.slice(Math.max(0, i - 2), i))
  return `${groups.join(',')},${tail}`
}

/** Parses a number with up to 2 decimals ("12", "12.5") into hundredths (1250). Null if invalid. */
export function parseHundredths(input: string): number | null {
  const m = /^(\d{1,12})(?:\.(\d{1,2}))?$/.exec(input.trim())
  if (!m) return null
  return parseInt(m[1], 10) * 100 + parseInt((m[2] ?? '').padEnd(2, '0') || '0', 10)
}

/** Inverse of parseHundredths for filling text fields: 1250 -> "12.5", 1200 -> "12". */
export function hundredthsToInput(h: number): string {
  const whole = Math.floor(h / 100)
  const frac = h % 100
  if (frac === 0) return String(whole)
  return `${whole}.${String(frac).padStart(2, '0').replace(/0$/, '')}`
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function formatDate(iso: string): string {
  const d = new Date(iso)
  return `${d.getDate()} ${MONTHS[d.getMonth()]}`
}

/** "Today", "Yesterday", or e.g. "12 Oct" (with the year when it isn't this year). */
export function dayLabel(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const days = Math.round((new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime() -
    new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return d.getFullYear() === now.getFullYear() ? formatDate(iso) : `${formatDate(iso)} ${d.getFullYear()}`
}
