import { formatPaise } from './money'

/** The message to send by hand when the app cannot notify the person itself (no device, or notifications are off). */
export function reminderMessage(name: string, amountPaise: number, groupName: string): string {
  return `Hi ${name}, a gentle reminder: you owe ${formatPaise(amountPaise)} in “${groupName}” on SplitBuys.`
}

/** The whole settle-up plan as plain text for a group chat. Names only (no "you"): it is read by other people. */
export function settleUpMessage(groupName: string, plan: { from: string; to: string; amountPaise: number }[]): string {
  if (plan.length === 0) return `Everyone is settled up in “${groupName}”.`
  return [`“${groupName}”: who pays whom`, ...plan.map((t) => `• ${t.from} pays ${t.to} ${formatPaise(t.amountPaise)}`), 'Tracked on SplitBuys'].join('\n')
}
