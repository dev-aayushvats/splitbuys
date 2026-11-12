// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { savedProfile, saveProfile } from './profileCache'

beforeEach(() => localStorage.clear())

describe('profile cache', () => {
  it('round-trips a profile', () => {
    saveProfile({ id: 3, email: 'a@x.in', name: null, upiId: 'a@b' })
    expect(savedProfile()).toEqual({ id: 3, email: 'a@x.in', name: null, upiId: 'a@b' })
  })
  it('rejects damaged or foreign entries instead of signing in as nobody', () => {
    for (const bad of ['{oops', 'null', '[]', '{"id":"3","email":"a"}', '{"id":3}']) {
      localStorage.setItem('profile', bad)
      expect(savedProfile()).toBeNull()
    }
  })
})
