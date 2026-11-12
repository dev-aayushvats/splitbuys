// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { byText, click, mount, type Mounted } from '../test/dom'
import { FeedbackProvider, useFeedback } from './feedback'

let api: ReturnType<typeof useFeedback>
function Grab() { api = useFeedback(); return null }
let m: Mounted

beforeEach(() => { vi.useFakeTimers(); m = mount(<FeedbackProvider><Grab /></FeedbackProvider>) })
afterEach(() => { m.unmount(); vi.useRealTimers() })

const status = () => m.host.ownerDocument.querySelector('[role=status]')

describe('toast', () => {
  it('shows a message and hides it after a few seconds', () => {
    act(() => api.toast('Saved'))
    expect(status()?.textContent).toBe('Saved')
    act(() => { vi.advanceTimersByTime(3300) })
    expect(status()).toBeNull()
  })
  it('keeps a toast with an action longer, because a button needs time to reach', () => {
    act(() => api.toast('Expense deleted', { label: 'Undo', run: () => {} }))
    act(() => { vi.advanceTimersByTime(3300) })
    expect(status()).not.toBeNull()
    act(() => { vi.advanceTimersByTime(3300) })
    expect(status()).toBeNull()
  })
  it('runs the action once and dismisses the toast when it is tapped', () => {
    const run = vi.fn()
    act(() => api.toast('Expense deleted', { label: 'Undo', run }))
    click(byText(m.host.ownerDocument.body, 'Undo'))
    expect(run).toHaveBeenCalledTimes(1)
    expect(status()).toBeNull()
  })
  it('a newer toast replaces the older one and restarts the clock', () => {
    act(() => api.toast('First'))
    act(() => { vi.advanceTimersByTime(3000) })
    act(() => api.toast('Second'))
    act(() => { vi.advanceTimersByTime(3000) })
    expect(status()?.textContent).toBe('Second')
  })
})

describe('confirm', () => {
  it('resolves true on the action and false on cancel', async () => {
    let answer: boolean | undefined
    act(() => { void api.confirm('Delete this?', 'Delete').then((a) => { answer = a }) })
    click(byText(m.host.ownerDocument.body, 'Delete'))
    await act(async () => {})
    expect(answer).toBe(true)

    act(() => { void api.confirm('Delete this?', 'Delete').then((a) => { answer = a }) })
    click(byText(m.host.ownerDocument.body, 'Cancel'))
    await act(async () => {})
    expect(answer).toBe(false)
  })
})
