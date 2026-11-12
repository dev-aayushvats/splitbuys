import { expect, it } from 'vitest'
import { CATEGORIES, categoryEmoji, categoryLabel } from './categories'

it('has a label for every category, ending with Other', () => {
  expect(CATEGORIES.map((c) => c.id)).toHaveLength(8)
  expect(CATEGORIES.at(-1)?.id).toBe('OTHER')
  for (const c of CATEGORIES) expect(categoryLabel(c.id)).toBe(c.label)
})

it('falls back to Other for an old or missing value', () => {
  expect(categoryLabel(undefined)).toBe('Other')
  expect(categoryLabel(null)).toBe('Other')
  expect(categoryEmoji(undefined)).toBe('🧾')
})
