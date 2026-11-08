import { describe, expect, it } from 'vitest'
import { withGroupName, withoutExpense, withoutMember, withoutSettlement, withSettlement } from './optimistic'
import type { GroupData, User } from './types'

const a: User = { id: 1, email: 'a@x.in', name: 'Asha' }
const b: User = { id: 2, email: 'b@x.in', name: 'Ben' }
// Asha paid 1000 split equally: Asha is owed 500, Ben owes 500. Ben has since paid Asha 100.
const base = (): GroupData => ({
  group: { id: 9, name: 'Trip', inviteToken: 't', members: [a, b] },
  expenses: [{ id: 10, description: 'Dinner', amountPaise: 1000, payer: a, createdBy: a, createdAt: '', splitType: 'EQUAL',
    shares: [{ user: a, amountPaise: 500 }, { user: b, amountPaise: 500 }] }],
  expensesCursor: null,
  settlements: [{ id: 20, fromUser: b, toUser: a, amountPaise: 100, createdAt: '' }],
  settlementsCursor: null,
  balances: { balances: [{ user: a, netPaise: 400 }, { user: b, netPaise: -400 }], totalSpentPaise: 1000,
    settleUp: [{ from: b, to: a, amountPaise: 400, upiId: 'a@upi', upiLink: 'upi://pay' }] },
})
const net = (d: GroupData) => d.balances.balances.map((x) => x.netPaise)

describe('optimistic updates', () => {
  it('removing an expense reverses its effect on balances and total', () => {
    const d = withoutExpense(base(), 10)
    expect(d.expenses).toEqual([])
    expect(d.balances.totalSpentPaise).toBe(0)
    expect(net(d)).toEqual([-100, 100]) // only Ben's 100 payment to Asha is left
  })
  it('removing a payment puts the debt back', () => {
    const d = withoutSettlement(base(), 20)
    expect(d.settlements).toEqual([])
    expect(net(d)).toEqual([500, -500])
  })
  it('recording a payment moves balances, adds a temporary entry and shrinks the plan', () => {
    const d = withSettlement(base(), 2, 1, 150)
    expect(net(d)).toEqual([250, -250])
    expect(d.settlements).toHaveLength(2)
    expect(d.settlements[0]).toMatchObject({ amountPaise: 150, fromUser: b, toUser: a })
    expect(d.balances.settleUp[0]).toMatchObject({ amountPaise: 250, upiLink: null })
  })
  it('paying the whole amount clears that transfer from the plan', () => {
    expect(withSettlement(base(), 2, 1, 400).balances.settleUp).toEqual([])
  })
  it('does not mutate its input, and ignores ids it does not know', () => {
    const d = base()
    withoutExpense(d, 10)
    expect(d.expenses).toHaveLength(1)
    expect(withoutExpense(d, 999)).toBe(d)
    expect(withoutSettlement(d, 999)).toBe(d)
    expect(withSettlement(d, 2, 77, 5)).toBe(d)
  })
  it('renames the group and removes a member with their balance row', () => {
    expect(withGroupName(base(), 'Goa').group.name).toBe('Goa')
    const d = withoutMember(base(), 2)
    expect(d.group.members).toEqual([a])
    expect(d.balances.balances).toHaveLength(1)
  })
})
