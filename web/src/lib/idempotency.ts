import { useRef } from 'react'

/**
 * The Idempotency-Key for one "create" action. The same key is reused while the user retries the same thing (so a repeat
 * after a timeout cannot create a second copy), and a fresh one is used as soon as the content changes (so an edited retry
 * is not mistaken for the old request). Call `done()` after success so that doing the same thing again on purpose, for
 * example paying the same amount twice, is a new action.
 */
export function useIdempotencyKey() {
  const last = useRef<{ fingerprint: string; key: string } | null>(null)
  return {
    keyFor(payload: unknown): string {
      const fingerprint = JSON.stringify(payload)
      if (last.current?.fingerprint !== fingerprint) last.current = { fingerprint, key: crypto.randomUUID() }
      return last.current.key
    },
    done() { last.current = null },
  }
}
