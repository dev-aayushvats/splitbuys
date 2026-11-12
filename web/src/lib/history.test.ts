import { describe, expect, it } from 'vitest'
import { changes, historyView } from './history'
import type { ExpenseSnapshot, HistoryEntry, User } from './types'

const me: User = { id: 1, email: 'a@x.com', name: 'Asha' }
const bo: User = { id: 2, email: 'b@x.com', name: 'Bo' }
const snap = (o: Partial<ExpenseSnapshot> = {}): ExpenseSnapshot => ({
  description: 'Lunch', amountPaise: 60000, payer: me, splitType: 'EQUAL',
  shares: [{ user: me, amountPaise: 30000 }, { user: bo, amountPaise: 30000 }], ...o,
})
const entry = (o: Partial<HistoryEntry>): HistoryEntry => ({
  id: 1, expenseId: 9, action: 'EDITED', actor: bo, at: '2026-01-01T00:00:00Z', before: snap(), after: snap(), undoable: false, ...o,
})

describe('changes', () => {
  it('names each thing that differs', () => {
    const after = snap({ description: 'Big lunch', amountPaise: 80000, payer: bo, shares: [{ user: me, amountPaise: 40000 }, { user: bo, amountPaise: 40000 }] })
    expect(changes(snap(), after, me)).toEqual([
      'name “Lunch” → “Big lunch”', 'amount ₹600.00 → ₹800.00', 'paid by you → bo', 'who owes what',
    ])
  })
  it('is empty when nothing visible differs', () => expect(changes(snap(), snap(), me)).toEqual([]))
})

describe('historyView', () => {
  it('reads an edit from the viewer’s side', () => {
    const v = historyView(entry({ after: snap({ amountPaise: 80000, shares: [{ user: me, amountPaise: 40000 }, { user: bo, amountPaise: 40000 }] }) }), me)
    expect(v.title).toBe('Bo edited “Lunch”')
    expect(v.detail).toBe('amount ₹600.00 → ₹800.00 · who owes what')
  })
  it('says "You" for the viewer’s own change', () => {
    expect(historyView(entry({ actor: me, action: 'DELETED', after: null }), me).title).toBe('You deleted “Lunch”')
  })
  it('describes creations and restores by the amount', () => {
    expect(historyView(entry({ action: 'CREATED', before: null }), me).detail).toBe('₹600.00')
    expect(historyView(entry({ action: 'RESTORED', before: null }), me).title).toBe('Bo restored “Lunch”')
  })
})
