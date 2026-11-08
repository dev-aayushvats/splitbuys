import type { GroupSummary } from './types'

export interface Overview {
  owed: number // total others owe the user, across groups where the user is in credit
  owe: number // total the user owes, across groups where the user is in debt
  net: number
  owedGroups: number
  oweGroups: number
}

export function summarize(groups: GroupSummary[]): Overview {
  const credit = groups.filter((g) => g.myBalancePaise > 0)
  const debt = groups.filter((g) => g.myBalancePaise < 0)
  const owed = credit.reduce((s, g) => s + g.myBalancePaise, 0)
  const owe = debt.reduce((s, g) => s - g.myBalancePaise, 0)
  return { owed, owe, net: owed - owe, owedGroups: credit.length, oweGroups: debt.length }
}

/** The one-line reading of the net figure that sits under it. */
export function headline(o: Overview, groupCount: number): string {
  if (groupCount === 0) return 'No groups yet'
  if (o.owed === 0 && o.owe === 0) return 'All settled up'
  if (o.net > 0) return "You're owed overall"
  if (o.net < 0) return 'You owe overall'
  return 'Owed and owing cancel out'
}

/** Share of the bar given to the "owed" side, in percent. Each non-zero side keeps a visible sliver. */
export function owedShare(o: Overview): number {
  const total = o.owed + o.owe
  if (total === 0) return 0
  const pct = (o.owed / total) * 100
  return o.owed === 0 ? 0 : o.owe === 0 ? 100 : Math.min(96, Math.max(4, pct))
}
