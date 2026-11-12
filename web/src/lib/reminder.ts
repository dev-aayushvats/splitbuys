import { formatPaise } from './money'

/** The message to send by hand when the app cannot notify the person itself (no device, or notifications are off). */
export function reminderMessage(name: string, amountPaise: number, groupName: string): string {
  return `Hi ${name}, a gentle reminder: you owe ${formatPaise(amountPaise)} in “${groupName}” on SplitBuys.`
}
