// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { persistOptions, queryClient, wipeLocalData } from './queryClient'

describe('local data', () => {
  it('sign-out empties the query cache and everything in storage', () => {
    queryClient.setQueryData(['u', 1, 'groups'], [{ id: 1 }])
    localStorage.setItem('token', 'secret')
    localStorage.setItem('splitbuys-cache', '{"clientState":{}}')
    sessionStorage.setItem('x', 'y')
    wipeLocalData()
    expect(queryClient.getQueryData(['u', 1, 'groups'])).toBeUndefined()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('persists only successful queries that opted in', () => {
    const should = persistOptions.dehydrateOptions.shouldDehydrateQuery
    expect(should({ state: { status: 'success' }, meta: { persist: true } })).toBe(true)
    expect(should({ state: { status: 'success' } })).toBe(false)
    expect(should({ state: { status: 'error' }, meta: { persist: true } })).toBe(false)
  })
})
