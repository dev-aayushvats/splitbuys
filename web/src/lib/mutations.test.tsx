// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { GroupData, User } from './types'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const toast = vi.fn()
const deleteExpense = vi.fn()
vi.mock('./api', () => ({ api: { deleteExpense: (...a: unknown[]) => deleteExpense(...a) } }))
vi.mock('./auth', () => ({ useAuth: () => ({ user: { id: 1 } }) }))
vi.mock('../ui', () => ({ useFeedback: () => ({ toast }) }))

import { useDeleteExpense } from './mutations'
import { keys } from './queries'

const a: User = { id: 1, email: 'a@x.in', name: 'Asha' }
const b: User = { id: 2, email: 'b@x.in', name: 'Ben' }
const data: GroupData = {
  group: { id: 9, name: 'Trip', inviteToken: 't', members: [a, b] },
  expenses: [{ id: 10, description: 'Dinner', amountPaise: 1000, payer: a, createdBy: a, createdAt: '', splitType: 'EQUAL', category: 'OTHER',
    shares: [{ user: a, amountPaise: 500 }, { user: b, amountPaise: 500 }] }],
  expensesCursor: null, settlements: [], settlementsCursor: null,
  balances: { balances: [{ user: a, netPaise: 500 }, { user: b, netPaise: -500 }], totalSpentPaise: 1000, settleUp: [] },
}

let host: HTMLDivElement
let root: Root
let qc: QueryClient
let mutate: (id: number) => void

function Probe() {
  mutate = useDeleteExpense(9).mutate
  return null
}

beforeEach(() => {
  host = document.createElement('div')
  root = createRoot(host)
  qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  qc.setQueryData(keys.group(1, 9), data)
  toast.mockClear()
  deleteExpense.mockReset()
  act(() => root.render(<QueryClientProvider client={qc}><Probe /></QueryClientProvider>))
})
afterEach(() => act(() => root.unmount()))

const cached = () => qc.getQueryData<GroupData>(keys.group(1, 9))!
const tick = () => act(() => new Promise((r) => setTimeout(r, 0)))

describe('optimistic delete', () => {
  it('removes the expense before the server answers', async () => {
    let finish!: () => void
    deleteExpense.mockReturnValue(new Promise<void>((r) => { finish = r }))
    act(() => mutate(10))
    await tick()
    expect(cached().expenses).toEqual([]) // already gone, request still pending
    finish()
    await tick()
  })

  it('puts the expense back and shows the error when the server refuses', async () => {
    deleteExpense.mockRejectedValue(new Error('Settle up first'))
    act(() => mutate(10))
    await tick()
    await tick()
    expect(toast).toHaveBeenCalledWith('Settle up first')
    expect(cached().expenses).toHaveLength(1)
    expect(cached().balances.totalSpentPaise).toBe(1000)
  })

  it('marks the friends list stale, since their balances are sums over groups', async () => {
    deleteExpense.mockResolvedValue(undefined)
    qc.setQueryData(keys.friends(1), [])
    expect(qc.getQueryState(keys.friends(1))?.isInvalidated).toBe(false)
    act(() => mutate(10))
    await tick()
    await tick()
    expect(qc.getQueryState(keys.friends(1))?.isInvalidated).toBe(true)
  })
})
