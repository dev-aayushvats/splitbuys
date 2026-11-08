import type { GroupData, User } from './types'

// Pure "what the screen would look like if this write succeeds" functions, applied to the cached data before the server
// answers. The server stays the source of truth: a refetch follows every write, and a failed write is rolled back.
// The suggested settle-up plan comes from the server's debt simplifier, so it is only adjusted where the answer is obvious.

const shift = (d: GroupData, userId: number, by: number): GroupData => ({
  ...d, balances: { ...d.balances, balances: d.balances.balances.map((b) => (b.user.id === userId ? { ...b, netPaise: b.netPaise + by } : b)) },
})

export function withoutExpense(d: GroupData, id: number): GroupData {
  const e = d.expenses.find((x) => x.id === id)
  if (!e) return d
  let next = shift(d, e.payer.id, -e.amountPaise)
  for (const s of e.shares) next = shift(next, s.user.id, s.amountPaise)
  return { ...next, expenses: d.expenses.filter((x) => x.id !== id), balances: { ...next.balances, totalSpentPaise: d.balances.totalSpentPaise - e.amountPaise } }
}

export function withoutSettlement(d: GroupData, id: number): GroupData {
  const s = d.settlements.find((x) => x.id === id)
  if (!s) return d
  const next = shift(shift(d, s.fromUser.id, -s.amountPaise), s.toUser.id, s.amountPaise)
  return { ...next, settlements: d.settlements.filter((x) => x.id !== id) }
}

export function withSettlement(d: GroupData, fromId: number, toId: number, amountPaise: number): GroupData {
  const find = (id: number): User | undefined => d.group.members.find((m) => m.id === id)
  const from = find(fromId), to = find(toId)
  if (!from || !to) return d
  const next = shift(shift(d, fromId, amountPaise), toId, -amountPaise)
  const plan = d.balances.settleUp.flatMap((t) => {
    if (t.from.id !== fromId || t.to.id !== toId) return [t]
    return amountPaise >= t.amountPaise ? [] : [{ ...t, amountPaise: t.amountPaise - amountPaise, upiLink: null }]
  })
  const temp = { id: -Date.now(), fromUser: from, toUser: to, amountPaise, createdAt: new Date().toISOString() }
  return { ...next, settlements: [temp, ...d.settlements], balances: { ...next.balances, settleUp: plan } }
}

export const withGroupName = (d: GroupData, name: string): GroupData => ({ ...d, group: { ...d.group, name } })

export const withoutMember = (d: GroupData, userId: number): GroupData => ({
  ...d,
  group: { ...d.group, members: d.group.members.filter((m) => m.id !== userId) },
  balances: { ...d.balances, balances: d.balances.balances.filter((b) => b.user.id !== userId) },
})
