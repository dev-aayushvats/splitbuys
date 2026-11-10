import { describe, expect, it } from 'vitest'
import { scrollProgress } from './navigation'

describe('scrollProgress', () => {
  it('runs from 0 at the top to 1 at the range and stays there', () => {
    expect(scrollProgress(0, 56)).toBe(0)
    expect(scrollProgress(28, 56)).toBe(0.5)
    expect(scrollProgress(56, 56)).toBe(1)
    expect(scrollProgress(900, 56)).toBe(1)
  })

  it('treats the iOS pull-down bounce (negative scroll) as the top', () => {
    expect(scrollProgress(-40, 56)).toBe(0)
  })
})
