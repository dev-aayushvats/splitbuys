import { describe, expect, it } from 'vitest'
import { mergeTimeline } from './timeline'

const e = (id: number, at: string) => ({ id, createdAt: at })
const names = (xs: { kind: string; item: { id: number } }[]) => xs.map((x) => `${x.kind[0]}${x.item.id}`)

describe('mergeTimeline', () => {
  it('shows everything, newest first, when nothing is left to load', () => {
    const t = mergeTimeline([e(1, '2026-01-01T10:00:00Z'), e(2, '2026-01-03T10:00:00Z')], false, [e(9, '2026-01-02T10:00:00Z')], false)
    expect(names(t)).toEqual(['e2', 's9', 'e1'])
  })

  it('hides payments older than the oldest loaded expense while more expenses remain', () => {
    // expenses loaded down to Jan 5; older ones not loaded yet. The Jan 2 payment must not appear above them.
    const t = mergeTimeline([e(1, '2026-01-09T00:00:00Z'), e(2, '2026-01-05T00:00:00Z')], true,
      [e(7, '2026-01-07T00:00:00Z'), e(8, '2026-01-02T00:00:00Z')], false)
    expect(names(t)).toEqual(['e1', 's7', 'e2'])
  })

  it('uses the newer cutoff when both lists have more', () => {
    const t = mergeTimeline([e(1, '2026-01-09T00:00:00Z'), e(2, '2026-01-04T00:00:00Z')], true,
      [e(7, '2026-01-08T00:00:00Z'), e(8, '2026-01-06T00:00:00Z')], true)
    expect(names(t)).toEqual(['e1', 's7', 's8'])
  })

  it('compares times as dates, not strings (fraction digits differ in length)', () => {
    const t = mergeTimeline([e(1, '2026-01-01T10:00:00.12Z')], false, [e(2, '2026-01-01T10:00:00.123Z')], false)
    expect(names(t)).toEqual(['s2', 'e1'])
  })

  it('handles empty lists', () => {
    expect(mergeTimeline([], false, [], false)).toEqual([])
    expect(mergeTimeline([], true, [], true)).toEqual([])
  })
})
