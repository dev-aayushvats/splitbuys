import { useEffect } from 'react'
import { swipeTarget } from '../lib/swipe'

// where a swipe must not switch tabs: typing, sliders, and anything in a sheet or dialog
const IGNORED = 'input, textarea, select, [contenteditable], [role="dialog"], [data-no-swipe]'

/**
 * Switch tabs with a sideways swipe anywhere on the page. `index` is the current tab, or -1 on pages that are not tabs
 * (the hook then does nothing). Listens on the window, like the app's other edge gestures, so it needs no wrapper element.
 */
export function useSwipeTabs(index: number, count: number, goTo: (i: number) => void) {
  useEffect(() => {
    if (index < 0) return
    let start: { x: number; y: number } | null = null
    const down = (e: TouchEvent) => {
      const t = e.touches[0]
      start = e.touches.length === 1 && !(e.target instanceof Element && e.target.closest(IGNORED)) ? { x: t.clientX, y: t.clientY } : null
    }
    const up = (e: TouchEvent) => {
      const s = start
      start = null
      if (!s) return
      const t = e.changedTouches[0]
      const to = swipeTarget({ startX: s.x, dx: t.clientX - s.x, dy: t.clientY - s.y }, index, count, window.innerWidth)
      if (to !== null) goTo(to)
    }
    const cancel = () => { start = null }
    window.addEventListener('touchstart', down, { passive: true })
    window.addEventListener('touchend', up, { passive: true })
    window.addEventListener('touchcancel', cancel, { passive: true })
    return () => {
      window.removeEventListener('touchstart', down)
      window.removeEventListener('touchend', up)
      window.removeEventListener('touchcancel', cancel)
    }
  }, [index, count, goTo])
}
