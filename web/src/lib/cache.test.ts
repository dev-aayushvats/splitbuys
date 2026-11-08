import { beforeEach, describe, expect, it } from 'vitest'
import { clearCache, peekCache, putCache } from './cache'

describe('cache', () => {
  beforeEach(clearCache)
  it('returns what was stored, and nothing for an unknown key', () => {
    putCache('groups', [1, 2])
    expect(peekCache('groups')).toEqual([1, 2])
    expect(peekCache('group:9')).toBeUndefined()
  })
  it('forgets everything when cleared', () => {
    putCache('groups', [1])
    clearCache()
    expect(peekCache('groups')).toBeUndefined()
  })
})
