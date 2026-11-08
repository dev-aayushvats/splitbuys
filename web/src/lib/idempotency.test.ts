// @vitest-environment jsdom
import { act, createElement } from 'react'
import { createRoot } from 'react-dom/client'
import { describe, expect, it } from 'vitest'
import { useIdempotencyKey } from './idempotency'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let hook: ReturnType<typeof useIdempotencyKey>
function Probe() {
  hook = useIdempotencyKey()
  return null
}
/** A fresh component instance (so a fresh key store) per call. */
function useHook() {
  const root = createRoot(document.createElement('div'))
  act(() => root.render(createElement(Probe)))
  return hook
}

describe('useIdempotencyKey', () => {
  it('keeps the key while the same thing is retried', () => {
    const h = useHook()
    expect(h.keyFor({ amount: 100 })).toBe(h.keyFor({ amount: 100 }))
  })

  it('uses a new key when the content changed, so an edited retry is not mistaken for the old request', () => {
    const h = useHook()
    expect(h.keyFor({ amount: 100 })).not.toBe(h.keyFor({ amount: 200 }))
  })

  it('uses a new key after success, so doing the same thing again on purpose is a new action', () => {
    const h = useHook()
    const first = h.keyFor({ amount: 100 })
    h.done()
    expect(h.keyFor({ amount: 100 })).not.toBe(first)
  })

  it('makes keys the backend accepts (8-64 letters, digits, - and _)', () => {
    expect(useHook().keyFor('x')).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
  })
})
