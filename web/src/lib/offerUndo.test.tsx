// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { mount, settle, type Mounted } from '../test/dom'
import type { HistoryEntry, User } from './types'

const toast = vi.fn()
const history = vi.fn()
const undoChange = vi.fn()
vi.mock('./api', () => ({ api: { history: (...a: unknown[]) => history(...a), undoChange: (...a: unknown[]) => undoChange(...a) } }))
vi.mock('./auth', () => ({ useAuth: () => ({ user: { id: 1, email: 'a@x.in', name: 'Asha' } }) }))
vi.mock('../ui', () => ({ useFeedback: () => ({ toast }) }))

import { useOfferUndo } from './mutations'

const me: User = { id: 1, email: 'a@x.in', name: 'Asha' }
const bo: User = { id: 2, email: 'b@x.in', name: 'Bo' }
const entry = (o: Partial<HistoryEntry>): HistoryEntry => ({ id: 5, expenseId: 10, action: 'DELETED', actor: me, at: '', before: null, after: null, undoable: true, ...o })

let offer: ReturnType<typeof useOfferUndo>
function Probe() { offer = useOfferUndo(); return null }
let m: Mounted

/** What the toast was given as its Undo action, and run it. */
const tapUndo = async () => {
  const [, action] = toast.mock.calls.at(-1)!
  await act(async () => { await action.run() })
  await settle()
}

beforeEach(() => { toast.mockReset(); history.mockReset(); undoChange.mockReset(); m = mount(<Probe />) })
afterEach(() => m.unmount())

describe('the Undo toast after a delete or edit', () => {
  it('offers Undo with a message that fits what happened, and fetches nothing until it is tapped', () => {
    act(() => offer(9, 10, 'DELETED'))
    expect(toast).toHaveBeenCalledWith('Expense deleted', expect.objectContaining({ label: 'Undo' }))
    act(() => offer(9, 10, 'EDITED'))
    expect(toast).toHaveBeenLastCalledWith('Expense updated', expect.objectContaining({ label: 'Undo' }))
    expect(history).not.toHaveBeenCalled()
  })

  it('undoes the user’s own change and says so', async () => {
    history.mockResolvedValue({ items: [entry({ id: 5 })], nextCursor: null })
    undoChange.mockResolvedValue(entry({}))
    act(() => offer(9, 10, 'DELETED'))
    await tapUndo()
    expect(undoChange).toHaveBeenCalledWith(9, 5)
    expect(toast).toHaveBeenLastCalledWith('Expense restored')
  })

  it('does not undo anything when someone else changed the expense in between', async () => {
    history.mockResolvedValue({ items: [entry({ id: 6, actor: bo, action: 'EDITED' }), entry({ id: 5 })], nextCursor: null })
    act(() => offer(9, 10, 'DELETED'))
    await tapUndo()
    expect(undoChange).not.toHaveBeenCalled()
    expect(toast.mock.lastCall![0]).toMatch(/Can't undo/)
  })

  it('shows the server’s refusal and still refreshes the screen', async () => {
    history.mockResolvedValue({ items: [entry({ id: 5 })], nextCursor: null })
    undoChange.mockRejectedValue(new Error('This expense has already been restored'))
    m.qc.setQueryData(['u', 1, 'groups'], [])
    act(() => offer(9, 10, 'DELETED'))
    await tapUndo()
    expect(toast).toHaveBeenLastCalledWith('This expense has already been restored')
    expect(m.qc.getQueryState(['u', 1, 'groups'])?.isInvalidated).toBe(true)
  })

  it('a failed lookup (offline) is reported, not swallowed', async () => {
    history.mockRejectedValue(new Error('Cannot reach the server. Check your connection.'))
    act(() => offer(9, 10, 'EDITED'))
    await tapUndo()
    expect(toast).toHaveBeenLastCalledWith('Cannot reach the server. Check your connection.')
  })
})
