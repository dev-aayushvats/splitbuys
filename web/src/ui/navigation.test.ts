import { describe, expect, it } from 'vitest'
import { mainTabIndex } from './navigation'

describe('mainTabIndex', () => {
  it('maps the four top-level pages to their tab', () => {
    expect(mainTabIndex('/')).toBe(0)
    expect(mainTabIndex('/friends')).toBe(1)
    expect(mainTabIndex('/activity')).toBe(2)
    expect(mainTabIndex('/profile')).toBe(3)
  })

  it('is -1 where the bar is hidden', () => {
    expect(mainTabIndex('/groups/7')).toBe(-1)
    expect(mainTabIndex('/groups/7/expense/new')).toBe(-1)
    expect(mainTabIndex('/join/abc')).toBe(-1)
    expect(mainTabIndex('/profiles')).toBe(-1)
  })
})
