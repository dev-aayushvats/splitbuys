import { dayLabel, formatPaise } from './money'
import { label, type ActivityItem, type User } from './types'

const who = (u: User, me: User) => (u.id === me.id ? 'You' : label(u))

export interface ActivityView {
  icon: 'receipt' | 'payments'
  title: string
  subtitle: string
  /** Right-hand text: what it did to the viewer ("you owe ₹200"), possibly on two lines. */
  note: string
  noteClass: string
}

/** How one feed item reads to the signed-in user. */
export function activityView(item: ActivityItem, me: User): ActivityView {
  if (item.type === 'PAYMENT' && item.payment) {
    const p = item.payment
    return {
      icon: 'payments',
      title: `${who(p.fromUser, me)} paid ${p.toUser.id === me.id ? 'you' : label(p.toUser)}`,
      subtitle: `Payment · ${item.groupName}`,
      note: formatPaise(p.amountPaise),
      noteClass: 'text-pos',
    }
  }
  const e = item.expense!
  const net = item.myNetPaise ?? 0
  const [note, noteClass] = net > 0 ? [`you lent\n${formatPaise(net)}`, 'text-pos']
    : net < 0 ? [`you owe\n${formatPaise(-net)}`, 'text-neg']
    : ['no change', 'text-muted']
  return {
    icon: 'receipt',
    title: e.description,
    subtitle: `${who(e.payer, me)} paid ${formatPaise(e.amountPaise)} · ${item.groupName}`,
    note,
    noteClass,
  }
}

/** Consecutive items under one "Today" / "Yesterday" / date heading. The feed is already newest first. */
export function groupByDay(items: ActivityItem[], now?: Date): { day: string; items: ActivityItem[] }[] {
  const days: { day: string; items: ActivityItem[] }[] = []
  for (const i of items) {
    const day = dayLabel(i.createdAt, now)
    const last = days[days.length - 1]
    if (last?.day === day) last.items.push(i)
    else days.push({ day, items: [i] })
  }
  return days
}
