import { describe, expect, it } from 'vitest'
import { emailMatches } from './account'

describe('emailMatches', () => {
  it('accepts the account email, ignoring case and surrounding spaces', () => {
    expect(emailMatches('asha@example.com', 'asha@example.com')).toBe(true)
    expect(emailMatches('  Asha@Example.COM ', 'asha@example.com')).toBe(true)
  })
  it('rejects anything else, including an empty box', () => {
    expect(emailMatches('', 'asha@example.com')).toBe(false)
    expect(emailMatches('   ', '')).toBe(false)
    expect(emailMatches('asha@example.co', 'asha@example.com')).toBe(false)
    expect(emailMatches('ben@example.com', 'asha@example.com')).toBe(false)
  })
})
