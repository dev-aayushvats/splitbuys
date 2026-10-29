import { formatPaise, hundredthsToInput, parseHundredths } from './money'
import type { SplitInput, SplitType } from './types'

export interface SplitResult { splits?: SplitInput[]; problem?: string; hint?: string }

interface Args {
  amount: string
  description: string
  kind: SplitType
  /** Selected member ids, in display order. */
  ids: number[]
  /** Raw field text per member (percent, exact amount or share count). */
  values: Record<number, string>
  nameOf: (id: number) => string
}

/** Validates the expense form and builds the `splits` the API expects, or explains what is wrong. */
export function evaluateSplit({ amount, description, kind, ids, values, nameOf }: Args): SplitResult {
  const total = parseHundredths(amount)
  if (!description.trim()) return { problem: 'Add a description' }
  if (total === null || total <= 0) return { problem: 'Enter a valid amount' }
  if (ids.length === 0) return { problem: 'Pick at least one person' }

  if (kind === 'EQUAL') {
    return { splits: ids.map((userId) => ({ userId })), hint: `${formatPaise(Math.floor(total / ids.length))} each` }
  }

  if (kind === 'SHARES') {
    const weights: Record<number, number> = {}
    for (const id of ids) {
      const t = (values[id] ?? '').trim()
      const w = t === '' ? 1 : /^\d+$/.test(t) ? parseInt(t, 10) : NaN
      if (Number.isNaN(w)) return { problem: 'Shares must be whole numbers' }
      weights[id] = w
    }
    if (Object.values(weights).every((w) => w === 0)) return { problem: 'At least one share must be above zero' }
    return { splits: ids.map((userId) => ({ userId, value: weights[userId] })), hint: 'Empty fields count as 1 share' }
  }

  const isPercent = kind === 'PERCENT'
  const amounts: Record<number, number> = {}
  for (const id of ids) {
    const t = (values[id] ?? '').trim()
    const v = t === '' ? 0 : parseHundredths(t)
    if (v === null) return { problem: `Check the number for ${nameOf(id)}` }
    amounts[id] = v
  }
  const sum = Object.values(amounts).reduce((a, b) => a + b, 0)
  const diff = (isPercent ? 10000 : total) - sum
  if (diff !== 0) {
    const left = isPercent ? `${hundredthsToInput(Math.abs(diff))}%` : formatPaise(Math.abs(diff))
    return { problem: diff > 0 ? `${left} left to assign` : `Over by ${left}` }
  }
  return {
    splits: ids.map((userId) => ({ userId, value: isPercent ? amounts[userId] / 100 : amounts[userId] })),
    hint: isPercent ? 'Adds up to 100%' : `Adds up to ${formatPaise(total)}`,
  }
}
