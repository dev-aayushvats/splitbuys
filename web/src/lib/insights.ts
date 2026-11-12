import type { MonthDetail, MonthSpending, SpendingInsights } from './types'

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

/**
 * Paid out against own share. "Covered" means the person paid more than their share (others owe them the difference, or
 * already paid it back), "owed" the reverse. Both counts are of the expenses of that month only.
 */
export function coverage(d: Pick<MonthDetail, 'paidPaise' | 'sharePaise'>): { kind: 'covered' | 'owed' | 'even'; paise: number } {
  const diff = d.paidPaise - d.sharePaise
  return { kind: diff > 0 ? 'covered' : diff < 0 ? 'owed' : 'even', paise: Math.abs(diff) }
}

/** Average share per day over the days counted (whole paise, rounded down). Zero when no day has been counted. */
export const perDay = (d: Pick<MonthDetail, 'sharePaise' | 'daysCounted'>) => (d.daysCounted > 0 ? Math.floor(d.sharePaise / d.daysCounted) : 0)

/** Each item's width as a share of the biggest one (0 to 1), for thin proportion bars. */
export function proportions<T extends { paise: number }>(items: T[]): number[] {
  const biggest = Math.max(0, ...items.map((i) => i.paise))
  return items.map((i) => (biggest > 0 ? i.paise / biggest : 0))
}
