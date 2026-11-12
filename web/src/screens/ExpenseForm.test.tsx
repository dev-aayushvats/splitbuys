// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { act } from 'react'
import { mount, settle, type, type Mounted } from '../test/dom'
import type { Expense, GroupDetail, User } from '../lib/types'

const { toast, ApiErr, addExpense, updateExpense } = vi.hoisted(() => ({
  toast: vi.fn(),
  addExpense: vi.fn(),
  updateExpense: vi.fn(),
  ApiErr: class extends Error { status?: number; constructor(m: string, s?: number) { super(m); this.status = s } },
}))
vi.mock('../lib/api', () => ({ ApiError: ApiErr, api: { addExpense: (...a: unknown[]) => addExpense(...a), updateExpense: (...a: unknown[]) => updateExpense(...a) } }))
vi.mock('../lib/auth', () => ({ useAuth: () => ({ user: { id: 1, email: 'a@x.in', name: 'Asha' } }) }))
vi.mock('../ui', async (orig) => ({ ...(await orig<typeof import('../ui')>()), useFeedback: () => ({ toast, confirm: async () => true }) }))
vi.mock('../lib/mutations', () => ({ useRefreshGroups: () => () => Promise.resolve(), useOfferUndo: () => () => {} }))

import { outbox } from '../lib/outbox'
import { ExpenseForm } from './ExpenseForm'

const a: User = { id: 1, email: 'a@x.in', name: 'Asha' }
const b: User = { id: 2, email: 'b@x.in', name: 'Bo' }
const group: GroupDetail = { id: 9, name: 'Trip', inviteToken: 't', members: [a, b] }
let m: Mounted
// jsdom has no layout, so it has no scrollIntoView
Element.prototype.scrollIntoView = () => {}

function open(expense: Expense | null = null) {
  m = mount(
    <MemoryRouter initialEntries={['/groups/9', '/groups/9/expense/new']} initialIndex={1}>
      <Routes>
        <Route path="/groups/9" element={<div>group page</div>} />
        <Route path="/groups/9/expense/new" element={<ExpenseForm group={group} expense={expense} />} />
      </Routes>
    </MemoryRouter>,
  )
}
const fill = (amount: string, what: string) => {
  type(m.host.querySelector<HTMLInputElement>('[aria-label=Amount]')!, amount)
  type(m.host.querySelector<HTMLInputElement>('input[autocapitalize=sentences]')!, what)
}
const submit = async () => { act(() => m.host.querySelector('form')!.requestSubmit()); await settle(); await settle() }

beforeEach(() => { outbox().clear(); toast.mockReset(); addExpense.mockReset(); updateExpense.mockReset() })
afterEach(() => { m.unmount(); outbox().clear() })

describe('saving a new expense', () => {
  it('sends it with an idempotency key and goes back to the group', async () => {
    addExpense.mockResolvedValue(undefined)
    open(); fill('450', 'Dinner')

    await submit()
    expect(addExpense).toHaveBeenCalledTimes(1)
    const [gid, body, key] = addExpense.mock.calls[0]
    expect(gid).toBe(9)
    expect(body).toMatchObject({ description: 'Dinner', amountPaise: 45000, payerId: 1, splitType: 'EQUAL' })
    expect(key).toMatch(/[0-9a-f-]{20,}/)
    expect(outbox().forUser(1)).toEqual([])
    expect(m.host.textContent).toContain('group page')
  })

  it('with no connection: keeps it on the device under the same key, goes back, and says so', async () => {
    addExpense.mockRejectedValue(new ApiErr('Cannot reach the server. Check your connection.'))
    open(); fill('450', 'Dinner'); await submit()
    const queued = outbox().forUser(1)
    expect(queued).toHaveLength(1)
    expect(queued[0]).toMatchObject({ gid: 9, groupName: 'Trip', key: addExpense.mock.calls[0][2] })
    expect(queued[0].body.description).toBe('Dinner')
    expect(toast.mock.lastCall![0]).toMatch(/Saved on this device/)
    expect(m.host.textContent).toContain('group page')
  })

  it('when the server refuses it: shows the reason, stays on the form, and queues nothing', async () => {
    addExpense.mockRejectedValue(new ApiErr('Amounts do not add up', 400))
    open(); fill('450', 'Dinner'); await submit()
    expect(toast).toHaveBeenCalledWith('Amounts do not add up')
    expect(outbox().forUser(1)).toEqual([])
    expect(m.host.textContent).not.toContain('group page')
  })

  it('a server error (5xx) is shown, not queued: the server did answer', async () => {
    addExpense.mockRejectedValue(new ApiErr('Something went wrong', 500))
    open(); fill('450', 'Dinner'); await submit()
    expect(outbox().forUser(1)).toEqual([])
    expect(toast).toHaveBeenCalledWith('Something went wrong')
  })
})

describe('editing an expense with no connection', () => {
  it('is not queued: an edit depends on what the expense looks like now', async () => {
    const e: Expense = { id: 3, description: 'Cab', amountPaise: 20000, payer: a, createdBy: a, createdAt: '2026-01-01T00:00:00Z', splitType: 'EQUAL', category: 'TRAVEL',
      shares: [{ user: a, amountPaise: 10000 }, { user: b, amountPaise: 10000 }] }
    updateExpense.mockRejectedValue(new ApiErr('Cannot reach the server. Check your connection.'))
    open(e); type(m.host.querySelector<HTMLInputElement>('input[autocapitalize=sentences]')!, 'Cab home'); await submit()
    expect(outbox().forUser(1)).toEqual([])
    expect(toast).toHaveBeenCalledWith('Cannot reach the server. Check your connection.')
    expect(m.host.textContent).not.toContain('group page')
  })
})
