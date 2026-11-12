import type { MonthSpending, SpendingInsights } from './types'

const NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** "2026-11" -> "Nov". */
export const monthName = (month: string) => NAMES[Number(month.slice(5, 7)) - 1] ?? month

export interface Bar {
  month: string
  label: string
  totalPaise: number
  /** 0 to 1 of the tallest bar, so the chart always uses its full height; a month with spending never vanishes. */
  height: number
}

/** Bars for the chart. Nothing is shown at all when there is no spending in the whole window. */
export function bars(data: SpendingInsights): Bar[] | null {
  const tallest = Math.max(...data.months.map((m) => m.totalPaise))
  if (!(tallest > 0)) return null
  return data.months.map((m) => ({
    month: m.month, label: monthName(m.month), totalPaise: m.totalPaise,
    height: m.totalPaise === 0 ? 0 : Math.max(0.06, m.totalPaise / tallest),
  }))
}

/** How the latest month compares with the one before: null when there is nothing to compare with. */
export function change(months: MonthSpending[]): { percent: number; up: boolean } | null {
  const [prev, now] = months.slice(-2)
  if (!prev || !now || prev.totalPaise === 0) return null
  const percent = Math.round(((now.totalPaise - prev.totalPaise) / prev.totalPaise) * 100)
  return { percent: Math.abs(percent), up: percent > 0 }
}
