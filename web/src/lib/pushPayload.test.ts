import { describe, expect, it } from 'vitest'
import { parsePayload, safeAppPath } from './pushPayload'

describe('safeAppPath', () => {
  it('keeps paths inside the app', () => {
    expect(safeAppPath('/groups/3')).toBe('/groups/3')
    expect(safeAppPath('/')).toBe('/')
    expect(safeAppPath('/groups/3?tab=2')).toBe('/groups/3?tab=2')
  })
  it('turns anything that could leave the app into the home page', () => {
    for (const bad of ['https://evil.example', '//evil.example', 'javascript:alert(1)', 'groups/3', '/\\evil.example', '/a b', '', null, undefined, 5, {}]) {
      expect(safeAppPath(bad), String(bad)).toBe('/')
    }
  })
})

describe('parsePayload', () => {
  it('reads a well-formed message', () => {
    expect(parsePayload({ title: 'Trip', body: 'Asha added Dinner', tag: 'group-3', url: '/groups/3', groupId: 3 }))
      .toEqual({ title: 'Trip', body: 'Asha added Dinner', tag: 'group-3', url: '/groups/3', groupId: 3 })
  })
  it('falls back to something sensible for a missing, odd or hostile message', () => {
    for (const raw of [null, undefined, 'text', 42, [], {}]) {
      const p = parsePayload(raw)
      expect(p.title).toBe('SplitBuys')
      expect(p.url).toBe('/')
      expect(p.groupId).toBeNull()
      expect(p.tag).toBeUndefined()
    }
    expect(parsePayload({ title: '   ', url: 'https://evil.example', groupId: '3' })).toMatchObject({ title: 'SplitBuys', url: '/', groupId: null })
  })
  it('limits how much text it will show', () => {
    expect(parsePayload({ title: 'x'.repeat(500), body: 'y'.repeat(500) })).toMatchObject({ title: 'x'.repeat(80), body: 'y'.repeat(200) })
  })
})
