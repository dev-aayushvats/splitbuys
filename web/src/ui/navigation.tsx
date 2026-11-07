import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { type IconName, Icon } from './icons'
import { tap } from './feedback'
import { RoundButton } from './controls'

/**
 * Floating header. The glass pane is itself a fixed element (blur is reliable there, where it is not for panes nested
 * inside a positioned wrapper or for sticky ones); a spacer keeps its place in the page flow. The fade is separate.
 */
/** Back one step, or home when the app was opened straight onto this page. */
export function useGoBack() {
  const nav = useNavigate()
  return useCallback(() => (window.history.state?.idx > 0 ? nav(-1) : nav('/', { replace: true })), [nav])
}

/** Installed iOS apps have no back swipe, so drag in from the left edge to go back (reachable with one thumb). */
function useEdgeSwipeBack(enabled: boolean) {
  const back = useGoBack()
  useEffect(() => {
    if (!enabled) return
    let start: { x: number; y: number } | null = null
    const down = (e: TouchEvent) => { const t = e.touches[0]; start = t.clientX <= 24 ? { x: t.clientX, y: t.clientY } : null }
    const up = (e: TouchEvent) => {
      if (!start) return
      const t = e.changedTouches[0]
      const dx = t.clientX - start.x, dy = t.clientY - start.y
      start = null
      if (dx > 80 && dx > Math.abs(dy) * 2) { tap(); back() }
    }
    window.addEventListener('touchstart', down, { passive: true })
    window.addEventListener('touchend', up, { passive: true })
    return () => { window.removeEventListener('touchstart', down); window.removeEventListener('touchend', up) }
  }, [enabled, back])
}

export function TopBar({ title, showBack = true, action }: { title: string; showBack?: boolean; action?: ReactNode }) {
  const back = useGoBack()
  useEdgeSwipeBack(showBack)
  return (
    <>
      <div aria-hidden className="h-[calc(4.75rem+env(safe-area-inset-top))] shrink-0" />
      {/* content dissolves into the page as it passes under the bar */}
      <div aria-hidden className="pointer-events-none fixed inset-x-0 top-0 z-20 mx-auto h-28 max-w-[480px] bg-gradient-to-b from-[#17181b]/90 via-[#17181b]/55 to-transparent" />
      <header className="glass-strong fixed left-1/2 top-[calc(0.75rem+env(safe-area-inset-top))] z-30 flex w-[calc(100%-2rem)] max-w-[448px] -translate-x-1/2 items-center gap-2 rounded-full p-1.5">
        <div className="w-11">
          {showBack && (
            <RoundButton label="Back" onClick={back}>
              <Icon name="back" />
            </RoundButton>
          )}
        </div>
        <h1 className="min-w-0 flex-1 truncate text-center text-[17px] font-semibold">{title}</h1>
        <div className="flex w-11 justify-end">{action}</div>
      </header>
    </>
  )
}

/**
 * Shared behaviour of the horizontally scrolling tab rows: a highlight that slides to the active tab (measured, so
 * tabs can be any width), the active tab scrolled into view, and edge fades while there is more to scroll to.
 */
function useScrollTabs(index: number, count: number) {
  const scroller = useRef<HTMLDivElement>(null)
  const items = useRef<(HTMLButtonElement | null)[]>([])
  const [box, setBox] = useState({ left: 0, width: 0 })
  const [edges, setEdges] = useState({ left: false, right: false })

  const measure = useCallback(() => {
    const el = items.current[index]
    const sc = scroller.current
    if (el) setBox({ left: el.offsetLeft, width: el.offsetWidth })
    if (sc) setEdges({ left: sc.scrollLeft > 2, right: sc.scrollLeft + sc.clientWidth < sc.scrollWidth - 2 })
  }, [index])

  // biome-ignore lint/correctness/useExhaustiveDependencies: measure again when the number of tabs changes
  useLayoutEffect(measure, [measure, count])
  useEffect(() => {
    items.current[index]?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' })
  }, [index])
  useEffect(() => {
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [measure])

  const fade = (e: { left: boolean; right: boolean }): CSSProperties => {
    const mask = `linear-gradient(to right, ${e.left ? 'transparent' : '#000'} 0, #000 22px, #000 calc(100% - 22px), ${e.right ? 'transparent' : '#000'} 100%)`
    return { WebkitMaskImage: mask, maskImage: mask }
  }
  return { scroller, items, box, onScroll: measure, style: fade(edges) }
}

/** Floating bottom navigation: a frosted pill with a sliding highlight. Scrolls sideways if it ever outgrows the screen. */
export function Dock({ items, index, onChange }: { items: { label: string; icon: IconName }[]; index: number; onChange: (i: number) => void }) {
  const t = useScrollTabs(index, items.length)
  return (
    <>
      {/* content dissolves into the page as it passes under the dock */}
      <div aria-hidden className="pointer-events-none fixed inset-x-0 bottom-0 z-20 mx-auto h-36 max-w-[480px] bg-gradient-to-t from-[#09090a] via-[#09090a]/80 to-transparent" />
      <nav className="glass-strong fixed bottom-[calc(1rem+env(safe-area-inset-bottom))] left-1/2 z-30 max-w-[calc(100%-2rem)] -translate-x-1/2 rounded-full p-1.5">
        <div ref={t.scroller} role="tablist" onScroll={t.onScroll} style={t.style} className="no-scrollbar relative flex snap-x overflow-x-auto rounded-full">
          <div aria-hidden className="absolute inset-y-0 rounded-full bg-white/[0.17] shadow-[inset_0_1px_0_rgb(255_255_255/0.32),0_4px_14px_-4px_rgb(0_0_0/0.4)] transition-[left,width] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
            style={{ left: t.box.left, width: t.box.width }} />
          {items.map((it, i) => (
            <button type="button" key={it.label} role="tab" aria-selected={i === index} ref={(el) => { t.items.current[i] = el }}
              onClick={() => { tap(); onChange(i) }}
              className={`relative z-10 flex h-14 min-w-[6.25rem] shrink-0 snap-center flex-col items-center justify-center gap-0.5 rounded-full px-3 text-[11px] font-semibold transition-colors duration-200 ${i === index ? 'text-accent' : 'text-muted'}`}>
              <Icon name={it.icon} size={21} />
              {it.label}
            </button>
          ))}
        </div>
      </nav>
    </>
  )
}

export function PillTabs({ labels, index, onChange }: { labels: string[]; index: number; onChange: (i: number) => void }) {
  const t = useScrollTabs(index, labels.length)
  return (
    <div className="glass-lite rounded-full p-1">
      <div ref={t.scroller} role="tablist" onScroll={t.onScroll} style={t.style} className="no-scrollbar relative flex snap-x overflow-x-auto rounded-full">
        <div aria-hidden className="btn-accent absolute inset-y-0 rounded-full transition-[left,width] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
          style={{ left: t.box.left, width: t.box.width }} />
        {labels.map((l, i) => (
          <button type="button" key={l} role="tab" aria-selected={i === index} ref={(el) => { t.items.current[i] = el }}
            onClick={() => { tap(); onChange(i) }}
            className={`relative z-10 min-w-[5.5rem] flex-1 shrink-0 snap-center whitespace-nowrap rounded-full px-4 py-2.5 text-[13.5px] font-bold transition-colors duration-200 ${i === index ? 'text-on-accent' : 'text-muted'}`}>
            {l}
          </button>
        ))}
      </div>
    </div>
  )
}
