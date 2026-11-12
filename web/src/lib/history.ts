import { categoryLabel } from './categories'
import { formatPaise } from './money'
import { label, type HistoryEntry, type User } from './types'

const who = (u: User, me: User) => (u.id === me.id ? 'You' : label(u))

/** What changed between two snapshots, as short phrases ("amount ₹600.00 → ₹800.00"). Empty when nothing visible differs. */
export function changes(before: NonNullable<HistoryEntry['before']>, after: NonNullable<HistoryEntry['after']>, me: User): string[] {
  const out: string[] = []
  if (before.description !== after.description) out.push(`name “${before.description}” → “${after.description}”`)
  if (before.amountPaise !== after.amountPaise) out.push(`amount ${formatPaise(before.amountPaise)} → ${formatPaise(after.amountPaise)}`)
  if (before.payer.id !== after.payer.id) out.push(`paid by ${who(before.payer, me).toLowerCase()} → ${who(after.payer, me).toLowerCase()}`)
  if ((before.category ?? 'OTHER') !== (after.category ?? 'OTHER')) out.push(`category ${categoryLabel(before.category ?? 'OTHER')} → ${categoryLabel(after.category ?? 'OTHER')}`)
  if (before.splitType !== after.splitType) out.push('split method')
  const owed = (s: typeof before) => [...s.shares].sort((a, b) => a.user.id - b.user.id).map((s) => `${s.user.id}:${s.amountPaise}`).join()
  if (owed(before) !== owed(after)) out.push('who owes what')
  return out
}

export interface HistoryView {
  title: string
  detail: string
}

/** How one history entry reads to the signed-in user. */
export function historyView(h: HistoryEntry, me: User): HistoryView {
  const actor = who(h.actor, me)
  const snap = h.after ?? h.before!
  const name = `“${snap.description}”`
  switch (h.action) {
    case 'CREATED':
      return { title: `${actor} added ${name}`, detail: formatPaise(snap.amountPaise) }
    case 'DELETED':
      return { title: `${actor} deleted ${name}`, detail: formatPaise(snap.amountPaise) }
    case 'RESTORED':
      return { title: `${actor} restored ${name}`, detail: formatPaise(snap.amountPaise) }
    case 'REVERTED':
      return { title: `${actor} undid an edit to ${name}`, detail: h.before && h.after ? changes(h.before, h.after, me).join(' · ') : '' }
    default:
      return { title: `${actor} edited ${name}`, detail: h.before && h.after ? changes(h.before, h.after, me).join(' · ') : '' }
  }
}
