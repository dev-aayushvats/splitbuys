import { describe, expect, it } from 'vitest'
import { bars, change, coverage, monthName, perDay, proportions } from './insights'
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

describe('coverage', () => {
  it('says who carried more', () => {
    expect(coverage({ paidPaise: 4000, sharePaise: 1800 })).toEqual({ kind: 'covered', paise: 2200 })
    expect(coverage({ paidPaise: 0, sharePaise: 1000 })).toEqual({ kind: 'owed', paise: 1000 })
    expect(coverage({ paidPaise: 500, sharePaise: 500 })).toEqual({ kind: 'even', paise: 0 })
  })
})
describe('perDay', () => {
  it('averages over the days counted, rounding down, and survives a zero', () => {
    expect(perDay({ sharePaise: 10000, daysCounted: 3 })).toBe(3333)
    expect(perDay({ sharePaise: 10000, daysCounted: 0 })).toBe(0)
  })
})
describe('proportions', () => {
  it('scales to the biggest and handles all-zero', () => {
    expect(proportions([{ paise: 200 }, { paise: 100 }])).toEqual([1, 0.5])
    expect(proportions([{ paise: 0 }])).toEqual([0])
    expect(proportions([])).toEqual([])
  })
})
