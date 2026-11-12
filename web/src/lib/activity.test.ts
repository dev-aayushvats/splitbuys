import { describe, expect, it } from 'vitest'
import { activityView, groupByDay } from './activity'
import type { ActivityItem, User } from './types'

const me: User = { id: 1, email: 'a@x.in', name: 'Asha' }
const bo: User = { id: 2, email: 'b@x.in', name: 'Bo' }

const expense = (over: Partial<ActivityItem> = {}, payer = me): ActivityItem => ({
  type: 'EXPENSE', id: 1, groupId: 9, groupName: 'Trip', createdAt: '2026-11-10T10:00:00Z', payment: null, myNetPaise: 0,
  expense: { id: 1, description: 'Hotel', amountPaise: 300000, payer, createdBy: payer, createdAt: '', splitType: 'EQUAL', category: 'OTHER', shares: [] },
  ...over,
})
const payment = (from: User, to: User): ActivityItem => ({
  type: 'PAYMENT', id: 2, groupId: 9, groupName: 'Trip', createdAt: '2026-11-10T11:00:00Z', expense: null, myNetPaise: null,
  payment: { id: 2, fromUser: from, toUser: to, amountPaise: 50000, createdAt: '' },
})

describe('activityView', () => {
  it('says what an expense did to me', () => {
    expect(activityView(expense({ myNetPaise: 200000 }), me)).toMatchObject({ title: 'Hotel', note: 'you lent\n₹2,000.00', noteClass: 'text-pos' })
    expect(activityView(expense({ myNetPaise: -40000 }, bo), me)).toMatchObject({ note: 'you owe\n₹400.00', noteClass: 'text-neg' })
    expect(activityView(expense({ myNetPaise: 0 }), me).note).toBe('no change')
  })

  it('names who paid and the group', () => {
    expect(activityView(expense(), me).subtitle).toBe('You paid ₹3,000.00 · Trip')
    expect(activityView(expense({}, bo), me).subtitle).toBe('Bo paid ₹3,000.00 · Trip')
  })

  it('reads a payment from my side and from theirs', () => {
    expect(activityView(payment(bo, me), me)).toMatchObject({ title: 'Bo paid you', subtitle: 'Payment · Trip', note: '₹500.00' })
    expect(activityView(payment(me, bo), me).title).toBe('You paid Bo')
  })
})

describe('groupByDay', () => {
  const now = new Date('2026-11-11T12:00:00')
  it('puts consecutive items under one heading', () => {
    const a = expense({ id: 1, createdAt: '2026-11-11T09:00:00' })
    const b = expense({ id: 2, createdAt: '2026-11-11T08:00:00' })
    const c = expense({ id: 3, createdAt: '2026-11-10T08:00:00' })
    const d = expense({ id: 4, createdAt: '2026-10-01T08:00:00' })
    expect(groupByDay([a, b, c, d], now).map((g) => [g.day, g.items.map((i) => i.id)])).toEqual([
      ['Today', [1, 2]], ['Yesterday', [3]], ['1 Oct', [4]],
    ])
  })
  it('is empty for no items', () => expect(groupByDay([], now)).toEqual([]))
})
