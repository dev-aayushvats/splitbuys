import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

/** Small helpers for component tests without a testing library: render into a detached div, let promises settle. */
export interface Mounted { host: HTMLDivElement; root: Root; qc: QueryClient; unmount: () => void }

export function mount(ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })): Mounted {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  act(() => root.render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>))
  return { host, root, qc, unmount: () => { act(() => root.unmount()); host.remove() } }
}

/** Lets pending promises and zero-delay timers run. */
export const settle = () => act(() => new Promise<void>((r) => setTimeout(r, 0)))

export const click = (el: Element | null) => { if (!el) throw new Error('nothing to click'); act(() => { (el as HTMLElement).click() }) }
export const byText = (host: ParentNode, text: string) =>
  [...host.querySelectorAll<HTMLElement>('button, a, [role=status], p, span, div')].find((e) => e.textContent?.trim() === text) ?? null
export const byLabel = (host: ParentNode, label: string) => host.querySelector<HTMLElement>(`[aria-label="${label}"]`)

/** Types into a React-controlled input the way a user does (a plain `.value =` would not reach React). */
export function type(input: HTMLInputElement, value: string) {
  const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
  act(() => { set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })) })
}
