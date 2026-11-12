// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { mount, settle, type Mounted } from '../test/dom'

const { toast, addExpense, ApiErr } = vi.hoisted(() => ({
  toast: vi.fn(),
  addExpense: vi.fn(),
  ApiErr: class extends Error { status?: number; constructor(m: string, s?: number) { super(m); this.status = s } },
}))
vi.mock('../lib/api', () => ({ ApiError: ApiErr, api: { addExpense: (...a: unknown[]) => addExpense(...a) } }))
vi.mock('../lib/auth', () => ({ useAuth: () => ({ user: { id: 1, email: 'a@x.in', name: 'Asha' } }) }))
vi.mock('./feedback', () => ({ useFeedback: () => ({ toast }) }))

import { outbox } from '../lib/outbox'
import { OutboxSync } from './OutboxSync'

const body = { description: 'Dinner', amountPaise: 45000, payerId: 1, splitType: 'EQUAL' as const, splits: [] }
const queue = (key: string, o: Record<string, unknown> = {}) => outbox().enqueue({ key, uid: 1, gid: 9, groupName: 'Trip', body, ...o })
let m: Mounted

beforeEach(() => { outbox().clear(); toast.mockReset(); addExpense.mockReset() })
afterEach(() => { m?.unmount(); outbox().clear() })

describe('syncing expenses added offline', () => {
  it('sends a waiting expense when the app opens, with its own key, then tells the user', async () => {
    queue('k1')
    addExpense.mockResolvedValue(undefined)
    m = mount(<OutboxSync />); await settle()
    expect(addExpense).toHaveBeenCalledWith(9, body, 'k1')
    expect(outbox().forUser(1)).toEqual([])
    expect(toast).toHaveBeenCalledWith('Synced “Dinner” · ₹450.00')
  })

  it('does nothing, and says nothing, when nothing is waiting', async () => {
    m = mount(<OutboxSync />); await settle()
    expect(addExpense).not.toHaveBeenCalled()
    expect(toast).not.toHaveBeenCalled()
  })

  it('keeps the expense when the connection is still down, and tries again when the browser says it is back', async () => {
    queue('k1')
    addExpense.mockRejectedValueOnce(new ApiErr('Cannot reach the server.')).mockResolvedValue(undefined)
    m = mount(<OutboxSync />); await settle()
    expect(outbox().waiting(1)).toHaveLength(1)
    expect(toast).not.toHaveBeenCalled()
    await act(async () => { window.dispatchEvent(new Event('online')) }); await settle()
    expect(addExpense).toHaveBeenCalledTimes(2)
    expect(outbox().forUser(1)).toEqual([])
  })

  it('keeps a refused expense visible with the reason, and tells the user', async () => {
    queue('k1')
    addExpense.mockRejectedValue(new ApiErr('You are not a member of this group', 404))
    m = mount(<OutboxSync />); await settle()
    expect(outbox().forUser(1)[0].error).toBe('You are not a member of this group')
    expect(outbox().waiting(1)).toHaveLength(0)
    expect(toast.mock.lastCall![0]).toMatch(/could not be saved: You are not a member/)
  })

  it('summarises several at once and never touches another user’s queue', async () => {
    queue('a'); queue('b'); queue('x', { uid: 2 })
    addExpense.mockResolvedValue(undefined)
    m = mount(<OutboxSync />); await settle()
    expect(toast).toHaveBeenCalledWith('Synced 2 expenses')
    expect(addExpense).toHaveBeenCalledTimes(2)
    expect(outbox().forUser(2)).toHaveLength(1)
  })

  it('marks the group’s data stale so the synced expense appears', async () => {
    queue('k1')
    addExpense.mockResolvedValue(undefined)
    m = mount(<OutboxSync />)
    m.qc.setQueryData(['u', 1, 'group', 9], { x: 1 })
    await settle()
    expect(m.qc.getQueryState(['u', 1, 'group', 9])?.isInvalidated).toBe(true)
  })
})
