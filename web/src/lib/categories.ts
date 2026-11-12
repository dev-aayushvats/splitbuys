import type { ExpenseCategory } from './types'

/** In the order the picker shows them. A short fixed list: free text would fragment ("food", "Food ", "dinner") and cannot be summed. */
export const CATEGORIES: { id: ExpenseCategory; label: string; emoji: string }[] = [
  { id: 'FOOD', label: 'Food', emoji: '🍽️' },
  { id: 'GROCERIES', label: 'Groceries', emoji: '🛒' },
  { id: 'TRAVEL', label: 'Travel', emoji: '🚕' },
  { id: 'STAY', label: 'Stay', emoji: '🏨' },
  { id: 'UTILITIES', label: 'Bills', emoji: '💡' },
  { id: 'ENTERTAINMENT', label: 'Fun', emoji: '🎬' },
  { id: 'SHOPPING', label: 'Shopping', emoji: '🛍️' },
  { id: 'OTHER', label: 'Other', emoji: '🧾' },
]

const BY_ID = new Map(CATEGORIES.map((c) => [c.id, c]))

export const categoryLabel = (id: ExpenseCategory | null | undefined) => BY_ID.get(id ?? 'OTHER')?.label ?? 'Other'
export const categoryEmoji = (id: ExpenseCategory | null | undefined) => BY_ID.get(id ?? 'OTHER')?.emoji ?? '🧾'
