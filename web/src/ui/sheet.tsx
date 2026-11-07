import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/** A floating glass panel. It animates out as well as in: the last open contents are kept while it fades away. */
export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title?: string; children: ReactNode }) {
  const [present, setPresent] = useState(open)
  const last = useRef({ title, children })
  if (open) last.current = { title, children }

  useEffect(() => {
    if (open) { setPresent(true); return }
    const t = window.setTimeout(() => setPresent(false), 220)
    return () => window.clearTimeout(t)
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  const [drag, setDrag] = useState(0)
  const from = useRef<number | null>(null)
  useEffect(() => { if (!open) setDrag(0) }, [open])

  if (!open && !present) return null
  const shown = last.current
  return createPortal(
    // biome-ignore lint/a11y/noStaticElementInteractions: clicking the backdrop is a mouse convenience; Escape (handled above) is the keyboard way to close
    <div className={`${open ? 'animate-fade' : 'animate-fade-out pointer-events-none'} fixed inset-0 z-50 flex items-end justify-center bg-black/45 backdrop-blur-[6px]`}
      role="presentation" onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div role="dialog" aria-modal="true"
        onTouchStart={(e) => { from.current = e.touches[0].clientY }}
        onTouchMove={(e) => { if (from.current !== null) setDrag(Math.max(0, e.touches[0].clientY - from.current)) }}
        onTouchEnd={() => { const far = drag > 90; from.current = null; if (far) onClose(); else setDrag(0) }}
        style={drag ? { transform: `translateY(${drag}px)`, opacity: 1 - Math.min(drag / 400, 0.5), transition: 'none' } : { transition: 'transform .35s cubic-bezier(0.22,1,0.36,1), opacity .35s' }}
        className={`${open ? 'animate-sheet' : 'animate-sheet-out'} glass-strong mb-[max(0.75rem,env(safe-area-inset-bottom))] w-[calc(100%-1.5rem)] max-w-[456px] rounded-[32px] px-5 pb-5 pt-3`}>
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-white/25" />
        {shown.title && <h2 className="mb-4 whitespace-pre-line text-lg font-bold">{shown.title}</h2>}
        {shown.children}
      </div>
    </div>,
    document.body,
  )
}
