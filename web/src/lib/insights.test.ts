import { describe, expect, it } from 'vitest'
import { bars, change, monthName } from './insights'
import type { MonthSpending } from './types'

const month = (m: string, totalPaise: number): MonthSpending => ({ month: m, totalPaise, categories: [] })
const six = (totals: number[]) => ({ months: totals.map((t, i) => month(`2026-${String(i + 6).padStart(2, '0')}`, t)) })

describe('bars', () => {
  it('scales to the tallest month and keeps small months visible', () => {
    const b = bars(six([0, 100, 400, 0, 25000, 1000]))!
    expect(b.map((x) => x.height)).toEqual([0, 0.06, 0.06, 0, 1, 0.06])
    expect(b[4].label).toBe('Oct')
  })
  it('shows nothing when there is no spending at all', () => expect(bars(six([0, 0, 0, 0, 0, 0]))).toBeNull())
})

describe('change', () => {
  it('compares the latest month with the one before', () => {
    expect(change(six([0, 0, 0, 0, 1000, 1500]).months)).toEqual({ percent: 50, up: true })
    expect(change(six([0, 0, 0, 0, 2000, 1500]).months)).toEqual({ percent: 25, up: false })
  })
  it('has nothing to say when last month was zero', () => expect(change(six([0, 0, 0, 0, 0, 1500]).months)).toBeNull())
})

it('names months', () => {
  expect(monthName('2026-01')).toBe('Jan')
  expect(monthName('2026-12')).toBe('Dec')
})
