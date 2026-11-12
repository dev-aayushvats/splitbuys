import { describe, expect, it } from 'vitest'
import { filterQuery, isFiltering, MAX_SEARCH, NO_FILTER } from './searchFilter'

describe('search filter', () => {
  it('is not filtering when nothing is typed or chosen, and whitespace does not count', () => {
    expect(isFiltering(NO_FILTER)).toBe(false)
    expect(isFiltering({ text: '   ', category: null })).toBe(false)
    expect(filterQuery({ text: '  ', category: null })).toBe('')
  })
  it('filters on text, on a category, or both', () => {
    expect(isFiltering({ text: 'cab', category: null })).toBe(true)
    expect(isFiltering({ text: '', category: 'FOOD' })).toBe(true)
    expect(filterQuery({ text: 'cab', category: 'TRAVEL' })).toBe('&q=cab&category=TRAVEL')
  })
  it('encodes what was typed, so & # % and spaces cannot change the request', () => {
    expect(filterQuery({ text: ' 100% a&b #1 ', category: null })).toBe('&q=100%25%20a%26b%20%231')
  })
  it('cuts text at the length the server accepts', () => {
    expect(filterQuery({ text: 'x'.repeat(MAX_SEARCH + 50), category: null })).toBe(`&q=${'x'.repeat(MAX_SEARCH)}`)
  })
})
