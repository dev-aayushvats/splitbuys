import { describe, expect, it } from 'vitest'
import { formatPaise, hundredthsToInput, parseHundredths } from './money'

describe('money', () => {
  it('formats with Indian grouping', () => {
    expect(formatPaise(12345678)).toBe('₹1,23,456.78')
    expect(formatPaise(99)).toBe('₹0.99')
    expect(formatPaise(-100000)).toBe('-₹1,000.00')
  })
  it('parses hundredths', () => {
    expect(parseHundredths('12')).toBe(1200)
    expect(parseHundredths('12.5')).toBe(1250)
    expect(parseHundredths('12.505')).toBeNull()
    expect(parseHundredths('abc')).toBeNull()
  })
  it('round-trips to input text', () => {
    expect(hundredthsToInput(1250)).toBe('12.5')
    expect(hundredthsToInput(1200)).toBe('12')
    expect(hundredthsToInput(1205)).toBe('12.05')
  })
})
