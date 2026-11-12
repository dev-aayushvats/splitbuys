import { describe, expect, it } from 'vitest'
import { suggestCategory } from './suggestCategory'
import type { ExpenseCategory } from './types'

const guess = (text: string) => suggestCategory(text)?.category ?? null

describe('words', () => {
  it.each([
    ['Dinner at Barbeque Nation', 'FOOD'], ['Zomato order', 'FOOD'], ['chai and samosa', 'FOOD'], ['Lunch 🍕', 'FOOD'],
    ['Big Basket', 'GROCERIES'], ['BigBasket', 'GROCERIES'], ['Blinkit', 'GROCERIES'], ['vegetables', 'GROCERIES'],
    ['Uber to airport', 'TRAVEL'], ['Petrol', 'TRAVEL'], ['Cab home', 'TRAVEL'], ['IRCTC tickets', 'TRAVEL'],
    ['Hotel booking', 'STAY'], ['Airbnb Goa', 'STAY'],
    ['Electricity bill', 'UTILITIES'], ['WiFi', 'UTILITIES'], ['Monthly rent', 'UTILITIES'],
    ['Movie night', 'ENTERTAINMENT'], ['Netflix', 'ENTERTAINMENT'], ['Birthday party', 'ENTERTAINMENT'],
    ['Amazon order', 'SHOPPING'], ['Gift for Asha', 'SHOPPING'],
  ] as [string, ExpenseCategory | null][])('%s -> %s', (text, expected) => expect(guess(text)).toBe(expected))

  it('prefers a phrase over a word inside it', () => {
    expect(guess('Uber Eats')).toBe('FOOD')
    expect(guess('Uber')).toBe('TRAVEL')
    expect(guess('car rent')).toBe('TRAVEL')
    expect(guess('house rent')).toBe('UTILITIES')
  })

  it('forgives a small typo in a longer word', () => {
    expect(guess('restarant')).toBe('FOOD')
    expect(guess('grocerys')).toBe('GROCERIES')
    expect(guess('electrcity')).toBe('UTILITIES')
  })

  it('does not guess when it is unsure', () => {
    for (const text of ['misc', 'stuff', 'ab', '', 'Bob', 'to Sam', 'things']) expect(guess(text)).toBeNull()
    expect(guess('dinner movie')).toBeNull() // food and entertainment tie
  })

  it('does not turn a short or unrelated word into a typo match', () => {
    expect(guess('bus')).toBe('TRAVEL')
    expect(guess('bug')).toBeNull()
    expect(guess('bark')).toBeNull()
  })
})

describe('history', () => {
  const earlier = [
    { description: 'Pandit ji', category: 'OTHER' as const },
    { description: 'Chai Point', category: 'FOOD' as const },
    { description: 'Raju bhai', category: 'GROCERIES' as const },
  ]
  it('uses what the group filed the same description under', () => {
    expect(suggestCategory('raju bhai', earlier)).toEqual({ category: 'GROCERIES', source: 'history' })
    expect(suggestCategory('  Raju   Bhai!', earlier)?.category).toBe('GROCERIES')
  })
  it('ignores earlier expenses left as Other, and falls back to words', () => {
    expect(suggestCategory('Pandit ji', earlier)).toBeNull()
    expect(suggestCategory('Chai', earlier)).toEqual({ category: 'FOOD', source: 'words' })
  })
  it('lets history win over words', () => {
    expect(suggestCategory('Uber', [{ description: 'uber', category: 'FOOD' }])?.category).toBe('FOOD')
  })
})
