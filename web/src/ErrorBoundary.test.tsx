// @vitest-environment jsdom
import { act, useState } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let host: HTMLDivElement
let root: Root
let errorLog: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  // React logs every caught render error; keep the test output readable
  errorLog = vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
  errorLog.mockRestore()
})

let shouldThrow = true
function Flaky() {
  if (shouldThrow) throw new Error('boom')
  return <p>all good</p>
}

describe('ErrorBoundary', () => {
  it('renders its children when nothing is wrong', () => {
    act(() => root.render(<ErrorBoundary><p>hello</p></ErrorBoundary>))
    expect(host.textContent).toBe('hello')
  })

  it('shows a way out instead of a blank page when a child throws while rendering', () => {
    shouldThrow = true
    act(() => root.render(<ErrorBoundary><Flaky /></ErrorBoundary>))
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong')
    expect([...host.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Try again', 'Reload app'])
    expect(errorLog).toHaveBeenCalled() // the failure is still reported to the console
  })

  it('"Try again" renders the children again, and recovers if the problem has gone', () => {
    shouldThrow = true
    act(() => root.render(<ErrorBoundary><Flaky /></ErrorBoundary>))
    shouldThrow = false
    act(() => { (host.querySelector('button') as HTMLButtonElement).click() })
    expect(host.textContent).toBe('all good')
  })

  it('shows the fallback again if the problem is still there after "Try again"', () => {
    shouldThrow = true
    act(() => root.render(<ErrorBoundary><Flaky /></ErrorBoundary>))
    act(() => { (host.querySelector('button') as HTMLButtonElement).click() })
    expect(host.textContent).toContain('Something went wrong')
  })

  it('leaves normal interaction untouched', () => {
    function Clicker() {
      const [n, setN] = useState(0)
      return <button onClick={() => { setN(n + 1) }}>clicks {n}</button>
    }
    act(() => root.render(<ErrorBoundary><Clicker /></ErrorBoundary>))
    act(() => { (host.querySelector('button') as HTMLButtonElement).click() })
    expect(host.textContent).toBe('clicks 1')
  })
})
