import { describe, expect, it } from 'vitest'
import { headline, owedShare, summarize } from './overview'
import type { GroupSummary } from './types'

const g = (id: number, myBalancePaise: number): GroupSummary => ({ id, name: `G${id}`, memberCount: 2, myBalancePaise })

describe('overview', () => {
  it('adds up each side separately and counts the groups behind it', () => {
    const o = summarize([g(1, 500), g(2, -200), g(3, 300), g(4, 0)])
    expect(o).toEqual({ owed: 800, owe: 200, net: 600, owedGroups: 2, oweGroups: 1 })
  })
  it('reads the net figure in words', () => {
    expect(headline(summarize([]), 0)).toBe('No groups yet')
    expect(headline(summarize([g(1, 0)]), 1)).toBe('All settled up')
    expect(headline(summarize([g(1, 100)]), 1)).toBe("You're owed overall")
    expect(headline(summarize([g(1, -100)]), 1)).toBe('You owe overall')
    expect(headline(summarize([g(1, 100), g(2, -100)]), 2)).toBe('Owed and owing cancel out')
  })
  it('splits the bar in proportion, keeping a sliver for a tiny side and nothing when empty', () => {
    expect(owedShare(summarize([g(1, 300), g(2, -100)]))).toBe(75)
    expect(owedShare(summarize([g(1, 1), g(2, -100000)]))).toBe(4)
    expect(owedShare(summarize([g(1, 100)]))).toBe(100)
    expect(owedShare(summarize([g(1, -100)]))).toBe(0)
    expect(owedShare(summarize([]))).toBe(0)
  })
})
