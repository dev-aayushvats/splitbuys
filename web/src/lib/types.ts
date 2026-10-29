export interface User { id: number; email: string; name: string | null }
export const label = (u: User) => (u.name ? u.name : u.email)

export interface GroupSummary { id: number; name: string; memberCount: number; myBalancePaise: number }
export interface GroupDetail { id: number; name: string; inviteToken: string; members: User[] }
export interface InvitePreview { groupName: string; memberCount: number; alreadyMember: boolean; groupId: number | null }

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENT' | 'SHARES'
export interface Share { user: User; amountPaise: number }
export interface Expense {
  id: number
  description: string
  amountPaise: number
  payer: User
  createdBy: User
  createdAt: string
  splitType: SplitType
  shares: Share[]
}
export interface Settlement { id: number; fromUser: User; toUser: User; amountPaise: number; createdAt: string }
export interface Balance { user: User; netPaise: number }
export interface Transfer { from: User; to: User; amountPaise: number }
export interface Balances { balances: Balance[]; settleUp: Transfer[] }

export interface GroupData {
  group: GroupDetail
  expenses: Expense[]
  settlements: Settlement[]
  balances: Balances
}

export interface SplitInput { userId: number; value?: number }
export interface ExpenseBody {
  description: string
  amountPaise: number
  payerId: number
  splitType: SplitType
  splits: SplitInput[]
}
