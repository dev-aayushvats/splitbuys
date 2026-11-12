export interface User { id: number; email: string; name: string | null }
/** The signed-in user's own account: unlike User it carries their private UPI ID. */
export interface Profile extends User { upiId: string | null }
export const label = (u: User) => (u.name ? u.name : u.email)

export interface GroupSummary { id: number; name: string; memberCount: number; myBalancePaise: number }
/** Positive: they owe the viewer. Negative: the viewer owes them. */
export interface FriendGroup { id: number; name: string; balancePaise: number }
export interface Friend { user: User; balancePaise: number; groups: FriendGroup[] }
export interface GroupDetail { id: number; name: string; inviteToken: string; members: User[] }
export interface InvitePreview { groupName: string; memberCount: number; alreadyMember: boolean; groupId: number | null }

/** One line of the cross-group feed. `expense` or `payment` is set to match `type`; `myNetPaise` (expenses only) is what it did to the viewer's balance. */
export interface ActivityItem {
  type: 'EXPENSE' | 'PAYMENT'
  id: number
  groupId: number
  groupName: string
  createdAt: string
  expense: Expense | null
  payment: Settlement | null
  myNetPaise: number | null
}

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENT' | 'SHARES'
export type ExpenseCategory = 'FOOD' | 'GROCERIES' | 'TRAVEL' | 'STAY' | 'UTILITIES' | 'ENTERTAINMENT' | 'SHOPPING' | 'OTHER'
export interface Share { user: User; amountPaise: number }
export interface Expense {
  id: number
  description: string
  amountPaise: number
  payer: User
  createdBy: User
  createdAt: string
  splitType: SplitType
  category: ExpenseCategory
  shares: Share[]
}
export interface Settlement { id: number; fromUser: User; toUser: User; amountPaise: number; createdAt: string }
export type HistoryAction = 'CREATED' | 'EDITED' | 'DELETED' | 'RESTORED' | 'REVERTED'
export interface ExpenseSnapshot { description: string; amountPaise: number; payer: User; splitType: SplitType; category: ExpenseCategory; shares: Share[] }
/** `mySharePaise` is the part of the category's spending that falls on the signed-in user. */
/** The signed-in user's own shares by month (oldest first; the last is the current month in IST), across the groups they are in. */
export interface CategoryAmount { category: ExpenseCategory; paise: number }
export interface MonthSpending { month: string; totalPaise: number; categories: CategoryAmount[] }
export interface SpendingInsights { months: MonthSpending[] }
export interface CategoryTotal { category: ExpenseCategory; totalPaise: number; count: number; mySharePaise: number }
export interface SpendingSummary { totalPaise: number; mySharePaise: number; categories: CategoryTotal[] }
/** One change to an expense; `before` is null for a creation and `after` for a deletion. */
export interface HistoryEntry {
  id: number
  expenseId: number
  action: HistoryAction
  actor: User
  at: string
  before: ExpenseSnapshot | null
  after: ExpenseSnapshot | null
  undoable: boolean
}
/** `delivered` is false when the person has no way to receive a notification: nothing was sent and the client should offer another way. */
export interface ReminderResult { delivered: boolean; amountPaise: number; nextAllowedAt: string | null }
export interface Balance { user: User; netPaise: number }
// upiId is present only when the signed-in user is the one who must pay and the receiver has set a UPI ID
export interface Transfer { from: User; to: User; amountPaise: number; upiId: string | null }
export interface Balances { balances: Balance[]; settleUp: Transfer[]; totalSpentPaise: number }
export interface Page<T> { items: T[]; nextCursor: string | null }

// the first page of expenses and payments; the cursors say whether (and where) older ones can be loaded
export interface GroupData {
  group: GroupDetail
  expenses: Expense[]
  expensesCursor: string | null
  settlements: Settlement[]
  settlementsCursor: string | null
  balances: Balances
}

export interface SplitInput { userId: number; value?: number }
export interface ExpenseBody {
  description: string
  amountPaise: number
  payerId: number
  splitType: SplitType
  category?: ExpenseCategory
  splits: SplitInput[]
}
