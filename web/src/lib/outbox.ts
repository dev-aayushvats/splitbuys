import { ApiError } from './api'
import type { ExpenseBody } from './types'

/**
 * Expenses added while there is no usable connection. Each one is kept on the device, under the same Idempotency-Key it will be
 * sent with, and sent in order once the connection is back. The key makes a repeat safe: if an earlier attempt did reach the
 * server but the answer was lost, the server returns the original result instead of creating a second copy.
 */
export interface Queued {
  /** The Idempotency-Key, and the identity of this item. */
  key: string
  uid: number
  gid: number
  groupName: string
  body: ExpenseBody
  queuedAt: string
  /** Set when the server refused it for good (the group is gone, the amount is invalid...). It stays listed until the user discards it. */
  error?: string
}

const STORAGE_KEY = 'splitbuys-outbox'

type Store = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export class Outbox {
  private items: Queued[]
  private listeners = new Set<() => void>()
  private flushing = false
  /** A new array on every change, so `useSyncExternalStore` sees it. */
  private snapshot: readonly Queued[]

  constructor(private readonly store: Store | null) {
    this.items = this.read()
    this.snapshot = [...this.items]
  }

  private read(): Queued[] {
    try {
      const parsed: unknown = JSON.parse(this.store?.getItem(STORAGE_KEY) ?? '[]')
      return Array.isArray(parsed) ? (parsed as Queued[]) : []
    } catch { return [] }
  }

  private commit(next: Queued[]) {
    this.items = next
    this.snapshot = [...next]
    try { next.length ? this.store?.setItem(STORAGE_KEY, JSON.stringify(next)) : this.store?.removeItem(STORAGE_KEY) } catch { /* storage full or unavailable: it still works until the page closes */ }
    for (const l of this.listeners) l()
  }

  subscribe = (l: () => void) => { this.listeners.add(l); return () => { this.listeners.delete(l) } }
  getSnapshot = () => this.snapshot

  forUser(uid: number): Queued[] { return this.items.filter((q) => q.uid === uid) }
  /** Items still waiting to be sent (not refused). */
  waiting(uid: number): Queued[] { return this.forUser(uid).filter((q) => !q.error) }

  enqueue(q: Omit<Queued, 'queuedAt'>) {
    if (this.items.some((i) => i.key === q.key)) return // the same action queued twice is still one action
    this.commit([...this.items, { ...q, queuedAt: new Date().toISOString() }])
  }
  remove(key: string) { this.commit(this.items.filter((q) => q.key !== key)) }
  fail(key: string, error: string) { this.commit(this.items.map((q) => (q.key === key ? { ...q, error } : q))) }
  /** Sign-out: nothing of this device's data may remain for the next person. */
  clear() { this.commit([]) }

  /**
   * Sends the user's waiting items one at a time, oldest first, so they land in the order they were made.
   * A temporary failure (no connection, timeout, server restarting) stops the run: the rest would fail the same way, and
   * order must be kept. A refusal by the server marks that one item and carries on with the next.
   */
  async flush(uid: number, send: (q: Queued) => Promise<unknown>): Promise<{ sent: Queued[]; refused: Queued[] }> {
    const result = { sent: [] as Queued[], refused: [] as Queued[] }
    if (this.flushing) return result
    this.flushing = true
    try {
      for (const q of this.waiting(uid)) {
        try {
          await send(q)
          this.remove(q.key)
          result.sent.push(q)
        } catch (e) {
          if (isTemporary(e)) break
          const message = e instanceof Error ? e.message : 'The server refused it'
          this.fail(q.key, message)
          result.refused.push({ ...q, error: message })
        }
      }
    } finally {
      this.flushing = false
    }
    return result
  }
}

/** No answer at all (offline, timed out), or an answer that says "try again": not a verdict on the expense itself. */
export function isTemporary(e: unknown): boolean {
  if (!(e instanceof ApiError)) return true
  return e.status === undefined || e.status >= 500 || e.status === 429 || e.status === 401
}

/** The failure that means "could not reach the server": the one case where an add is kept for later instead of being shown as an error. */
export const isUnreachable = (e: unknown) => e instanceof ApiError && e.status === undefined

let shared: Outbox | undefined
/** The one queue for this page. Created on first use so tests can build their own `Outbox`. */
export function outbox(): Outbox {
  shared ??= new Outbox(typeof window === 'undefined' ? null : safeStorage())
  return shared
}
function safeStorage(): Store | null { try { return window.localStorage } catch { return null } }
