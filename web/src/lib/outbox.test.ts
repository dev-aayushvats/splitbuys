import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { isTemporary, isUnreachable, Outbox, type Queued } from './outbox'

const memory = () => {
  const m = new Map<string, string>()
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v), removeItem: (k: string) => void m.delete(k), m }
}
const body = { description: 'Dinner', amountPaise: 1000, payerId: 1, splitType: 'EQUAL' as const, splits: [] }
const item = (key: string, o: Partial<Queued> = {}) => ({ key, uid: 1, gid: 9, groupName: 'Trip', body, ...o })

describe('Outbox', () => {
  it('keeps items across a reload of the page', () => {
    const s = memory()
    new Outbox(s).enqueue(item('a'))
    expect(new Outbox(s).waiting(1).map((q) => q.key)).toEqual(['a'])
  })
  it('queues the same action only once', () => {
    const box = new Outbox(memory())
    box.enqueue(item('a')); box.enqueue(item('a'))
    expect(box.forUser(1)).toHaveLength(1)
  })
  it('lists only the signed-in user’s items', () => {
    const box = new Outbox(memory())
    box.enqueue(item('a')); box.enqueue(item('b', { uid: 2 }))
    expect(box.forUser(1).map((q) => q.key)).toEqual(['a'])
  })
  it('survives unreadable storage', () => {
    const s = memory(); s.setItem('splitbuys-outbox', '{oops')
    expect(new Outbox(s).forUser(1)).toEqual([])
    expect(() => new Outbox(null).enqueue(item('a'))).not.toThrow()
  })
  it('notifies subscribers with a new snapshot on every change', () => {
    const box = new Outbox(memory()); const l = vi.fn(); box.subscribe(l)
    const before = box.getSnapshot(); box.enqueue(item('a'))
    expect(l).toHaveBeenCalledTimes(1)
    expect(box.getSnapshot()).not.toBe(before)
  })
  it('empties the storage entry when the queue empties', () => {
    const s = memory(); const box = new Outbox(s)
    box.enqueue(item('a')); box.remove('a')
    expect(s.m.size).toBe(0)
  })
})

describe('flush', () => {
  it('sends oldest first, with each item’s own key, and removes what was sent', async () => {
    const box = new Outbox(memory()); box.enqueue(item('a')); box.enqueue(item('b'))
    const sent: string[] = []
    const r = await box.flush(1, async (q) => { sent.push(q.key) })
    expect(sent).toEqual(['a', 'b'])
    expect(r.sent).toHaveLength(2)
    expect(box.forUser(1)).toEqual([])
  })
  it('stops at the first temporary failure and keeps the rest in order', async () => {
    const box = new Outbox(memory()); box.enqueue(item('a')); box.enqueue(item('b')); box.enqueue(item('c'))
    const send = vi.fn(async (q: Queued) => { if (q.key === 'b') throw new ApiError('Cannot reach the server.') })
    const r = await box.flush(1, send)
    expect(send).toHaveBeenCalledTimes(2)
    expect(r.sent.map((q) => q.key)).toEqual(['a'])
    expect(box.waiting(1).map((q) => q.key)).toEqual(['b', 'c'])
  })
  it('marks a refused item, keeps it listed, and carries on with the next', async () => {
    const box = new Outbox(memory()); box.enqueue(item('a')); box.enqueue(item('b'))
    const r = await box.flush(1, async (q) => { if (q.key === 'a') throw new ApiError('Group not found', 404) })
    expect(r.refused.map((q) => [q.key, q.error])).toEqual([['a', 'Group not found']])
    expect(r.sent.map((q) => q.key)).toEqual(['b'])
    expect(box.forUser(1).map((q) => [q.key, q.error])).toEqual([['a', 'Group not found']])
    expect(box.waiting(1)).toEqual([]) // a refused item is not retried
  })
  it('does not run two flushes at once', async () => {
    const box = new Outbox(memory()); box.enqueue(item('a'))
    let release!: () => void
    const first = box.flush(1, () => new Promise<void>((r) => { release = r }))
    const second = await box.flush(1, async () => { throw new Error('must not run') })
    expect(second.sent).toEqual([])
    release(); await first
    expect(box.forUser(1)).toEqual([])
  })
  it('leaves other users’ items alone', async () => {
    const box = new Outbox(memory()); box.enqueue(item('x', { uid: 2 }))
    const send = vi.fn(); await box.flush(1, send)
    expect(send).not.toHaveBeenCalled()
  })
})

describe('what counts as temporary', () => {
  it('treats no answer, 5xx, 429 and 401 as temporary, and other 4xx as a verdict', () => {
    expect(isTemporary(new ApiError('offline'))).toBe(true)
    expect(isTemporary(new ApiError('x', 503))).toBe(true)
    expect(isTemporary(new ApiError('x', 429))).toBe(true)
    expect(isTemporary(new ApiError('x', 401))).toBe(true)
    expect(isTemporary(new ApiError('x', 400))).toBe(false)
    expect(isTemporary(new ApiError('x', 404))).toBe(false)
    expect(isTemporary(new ApiError('x', 409))).toBe(false)
  })
  it('keeps an add for later only when the server could not be reached at all', () => {
    expect(isUnreachable(new ApiError('offline'))).toBe(true)
    expect(isUnreachable(new ApiError('x', 500))).toBe(false)
    expect(isUnreachable(new Error('boom'))).toBe(false)
  })
})
