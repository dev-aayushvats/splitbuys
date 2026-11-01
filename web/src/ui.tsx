import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { formatPaise } from './lib/money'
import { label, type User } from './lib/types'

/* ---------- icons ---------- */
const PATHS = {
  back: 'M15 18l-6-6 6-6',
  plus: 'M12 5v14M5 12h14',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12M9 7V4h6v3',
  copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
  check: 'M5 13l4 4L19 7',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6',
  wallet: 'M3 7h16a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2zM3 7l2-3h12M16 14h.01',
  groups: 'M9 11a3 3 0 100-6 3 3 0 000 6zM3 20a6 6 0 0112 0M17 11a2.5 2.5 0 100-5M17 14.5A5 5 0 0121 20',
  payments: 'M3 6h18v12H3zM12 15a3 3 0 100-6 3 3 0 000 6z',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  logout: 'M9 4H5a1 1 0 00-1 1v14a1 1 0 001 1h4M16 8l4 4-4 4M20 12H9',
  split: 'M6 4v6q0 4 4 4h8M6 20v-6M15 10l4 4-4 4',
} as const
export type IconName = keyof typeof PATHS

export function Icon({ name, size = 20, className = '' }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d={PATHS[name]} />
    </svg>
  )
}

/* ---------- toast + confirm ---------- */
interface Feedback {
  toast: (msg: string) => void
  confirm: (title: string, action?: string) => Promise<boolean>
}
const FeedbackCtx = createContext<Feedback>(null!)
export const useFeedback = () => useContext(FeedbackCtx)

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null)
  const timer = useRef<number>(0)
  const [ask, setAsk] = useState<{ title: string; action: string; done: (ok: boolean) => void } | null>(null)

  const toast = useCallback((m: string) => {
    setMsg(m)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMsg(null), 3200)
  }, [])
  const confirm = useCallback(
    (title: string, action = 'Delete') => new Promise<boolean>((done) => setAsk({ title, action, done })),
    [],
  )
  const close = (ok: boolean) => { ask?.done(ok); setAsk(null) }

  return (
    <FeedbackCtx.Provider value={{ toast, confirm }}>
      {children}
      {msg && (
        <div role="status" className="glass-strong animate-rise fixed bottom-28 left-1/2 z-[60] w-max max-w-[calc(100%-3rem)] -translate-x-1/2 rounded-full px-5 py-3 text-center text-sm font-medium">
          {msg}
        </div>
      )}
      <Sheet open={!!ask} onClose={() => close(false)} title={ask?.title}>
        <div className="flex gap-3">
          <button type="button" className="h-13 flex-1 rounded-full glass-btn py-3.5 font-semibold" onClick={() => close(false)}>Cancel</button>
          <button type="button" className="h-13 flex-1 rounded-full bg-neg py-3.5 font-bold text-on-accent shadow-[0_10px_28px_-8px_rgb(255_90_90/0.6),inset_0_1px_0_rgb(255_255_255/0.4)]" onClick={() => close(true)}>{ask?.action}</button>
        </div>
      </Sheet>
    </FeedbackCtx.Provider>
  )
}

/** Light haptic tick where the browser supports it (Android); a no-op elsewhere. */
export const tap = () => { try { navigator.vibrate?.(8) } catch { /* unsupported */ } }

export const Skeleton = ({ className = '' }: { className?: string }) => (
  <div aria-hidden className={`glass-lite relative overflow-hidden rounded-2xl ${className}`}>
    <div className="animate-shimmer absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/[0.09] to-transparent" />
  </div>
)

export function EmptyState({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="animate-rise flex flex-col items-center px-6 py-10 text-center">
      <div className="grid size-16 place-items-center rounded-full bg-accent/10 text-accent ring-8 ring-accent/5"><Icon name={icon} size={28} /></div>
      <div className="mt-5 text-lg font-bold">{title}</div>
      {children && <p className="mt-1.5 max-w-[16rem] leading-relaxed text-muted">{children}</p>}
    </div>
  )
}

/* ---------- layout ---------- */
export function Screen({ children, className = '', onRefresh }: { children: ReactNode; className?: string; onRefresh?: () => void }) {
  return (
    <div className={`animate-fade mx-auto flex min-h-dvh w-full max-w-[480px] flex-col overflow-x-clip ${className}`}>
      {onRefresh && <PullIndicator onRefresh={onRefresh} />}
      {children}
    </div>
  )
}

/** Touch pull-down at the top of the page; fires onRefresh past a threshold. */
function PullIndicator({ onRefresh }: { onRefresh: () => void }) {
  const [pull, setPull] = useState(0)
  const [busy, setBusy] = useState(false)
  const start = useRef<number | null>(null)
  const latest = useRef({ pull: 0, busy: false, onRefresh })
  latest.current = { pull, busy, onRefresh }

  useEffect(() => {
    const down = (e: TouchEvent) => { start.current = window.scrollY <= 0 && !latest.current.busy ? e.touches[0].clientY : null }
    const move = (e: TouchEvent) => {
      if (start.current === null) return
      const dy = e.touches[0].clientY - start.current
      setPull(dy > 0 && window.scrollY <= 0 ? Math.min(dy * 0.45, 84) : 0)
    }
    const up = () => {
      const { pull: p, onRefresh: refresh } = latest.current
      start.current = null
      if (p >= 56) {
        setBusy(true)
        setPull(56)
        refresh()
        window.setTimeout(() => { setBusy(false); setPull(0) }, 700)
      } else setPull(0)
    }
    window.addEventListener('touchstart', down, { passive: true })
    window.addEventListener('touchmove', move, { passive: true })
    window.addEventListener('touchend', up)
    window.addEventListener('touchcancel', up)
    return () => {
      window.removeEventListener('touchstart', down)
      window.removeEventListener('touchmove', move)
      window.removeEventListener('touchend', up)
      window.removeEventListener('touchcancel', up)
    }
  }, [])

  const progress = Math.min(pull / 56, 1)
  return (
    <div aria-hidden className="glass-strong pointer-events-none fixed left-1/2 top-[env(safe-area-inset-top)] z-40 grid size-9 -translate-x-1/2 place-items-center rounded-full"
      style={{ transform: `translateY(${pull - 40}px)`, opacity: progress, transition: start.current === null ? 'transform .25s, opacity .25s' : 'none' }}>
      {busy ? <Spinner className="size-4" /> : <Icon name="arrow" size={16} className="rotate-90 text-accent" />}
    </div>
  )
}

export function Spinner({ className = '' }: { className?: string }) {
  return <div className={`size-6 animate-spin rounded-full border-2 border-accent border-t-transparent ${className}`} role="progressbar" />
}

export function Centered({ children }: { children: ReactNode }) {
  return <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center text-muted">{children}</div>
}

export function ErrorRetry({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <Centered>
      <p className="text-ink">{error instanceof Error ? error.message : 'Something went wrong'}</p>
      <button type="button" onClick={onRetry} className="px-4 py-2 font-semibold text-accent">Retry</button>
    </Centered>
  )
}

/** A dashed-outline section panel; call sites pass padding/radius. */
export const Dashed = ({ children, className = 'p-4' }: { children: ReactNode; className?: string }) => (
  <div className={`rounded-[28px] border border-dashed border-white/20 bg-white/[0.03] ${className}`}>{children}</div>
)

export function RoundButton({ children, onClick, label: aria, disabled }: { children: ReactNode; onClick?: () => void; label: string; disabled?: boolean }) {
  return (
    <button type="button" aria-label={aria} onClick={onClick} disabled={disabled}
      className="glass-btn grid size-11 place-items-center rounded-full disabled:text-muted">
      {children}
    </button>
  )
}

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
      <div aria-hidden className="pointer-events-none fixed inset-x-0 top-0 z-20 mx-auto h-28 max-w-[480px] bg-gradient-to-b from-[#1a2236]/90 via-[#1a2236]/55 to-transparent" />
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
 * The page behind everything: a still, muted slate-navy gradient with a soft cool light at the top and a little grain.
 * It stays quiet on purpose; the grain and light give the glass panes something to frost over.
 */
export function Backdrop() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 -z-10 overflow-hidden bg-gradient-to-b from-[#1a2236] via-[#131827] to-[#0d111b]">
      <div className="absolute inset-x-0 top-0 h-[60%] bg-[radial-gradient(ellipse_90%_70%_at_30%_0%,rgb(120_140_190/0.16),transparent_70%)]" />
      <svg className="absolute inset-0 size-full opacity-[0.06] mix-blend-overlay">
        <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" stitchTiles="stitch" /></filter>
        <rect width="100%" height="100%" filter="url(#grain)" />
      </svg>
    </div>
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
      <div aria-hidden className="pointer-events-none fixed inset-x-0 bottom-0 z-20 mx-auto h-36 max-w-[480px] bg-gradient-to-t from-[#0d111b] via-[#0d111b]/80 to-transparent" />
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

/* ---------- avatars ---------- */
const PALETTES = [
  ['#B26BFF', '#FF7EB6'], ['#4FACFE', '#00D4E6'], ['#FF9A44', '#FC6076'],
  ['#43E97B', '#2BD9C0'], ['#FFD84D', '#FF9F43'], ['#7F7FD5', '#86A8E7'],
]

export function Avatar({ user, size = 44 }: { user: User; size?: number }) {
  const [a, b] = PALETTES[user.id % PALETTES.length]
  return (
    <div className="grid shrink-0 place-items-center rounded-full font-extrabold text-on-accent shadow-[0_6px_16px_-4px_rgb(0_0_0/0.5),inset_0_1px_0_rgb(255_255_255/0.45)] ring-1 ring-white/25"
      style={{ width: size, height: size, fontSize: size * 0.42, background: `linear-gradient(135deg, ${a}, ${b})` }}>
      {[...label(user)][0]?.toUpperCase()}
    </div>
  )
}

export function AvatarStack({ users, size = 34, max = 4 }: { users: User[]; size?: number; max?: number }) {
  const shown = users.slice(0, max)
  const extra = users.length - shown.length
  const step = size * 0.62
  const count = shown.length + (extra > 0 ? 1 : 0)
  const ring = 'absolute rounded-full bg-[#131827] p-0.5'
  return (
    <div className="relative shrink-0" style={{ width: size + 4 + step * (count - 1), height: size + 4 }}>
      {shown.map((u, i) => (
        <div key={u.id} className={ring} style={{ left: i * step }}><Avatar user={u} size={size} /></div>
      ))}
      {extra > 0 && (
        <div className={ring} style={{ left: shown.length * step }}>
          <div className="grid place-items-center rounded-full bg-white/15 font-bold ring-1 ring-white/20" style={{ width: size, height: size, fontSize: size * 0.34 }}>+{extra}</div>
        </div>
      )}
    </div>
  )
}

/* ---------- controls ---------- */
export function ConfirmButton({ children, onClick, busy, disabled, type = 'button' }: {
  children: ReactNode; onClick?: () => void; busy?: boolean; disabled?: boolean; type?: 'button' | 'submit'
}) {
  return (
    <button type={type} onClick={onClick} disabled={disabled || busy}
      className="btn-accent flex h-15 w-full items-center rounded-full pl-1.5 pr-14 text-base font-bold transition-[transform,opacity] disabled:opacity-45">
      <span className="grid size-12 shrink-0 place-items-center rounded-full bg-on-accent text-accent">
        {busy ? <Spinner className="size-5" /> : <Icon name="arrow" size={22} />}
      </span>
      <span className="flex-1 text-center">{children}</span>
    </button>
  )
}

export function PillButton({ children, onClick, disabled, className = '' }: { children: ReactNode; onClick?: () => void; disabled?: boolean; className?: string }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled}
      className={`btn-accent inline-flex h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-bold disabled:bg-none disabled:bg-white/10 disabled:text-muted disabled:shadow-none ${className}`}>
      {children}
    </button>
  )
}

export const inputCls =
  'w-full rounded-2xl glass-btn px-4 py-4 shadow-[inset_0_2px_8px_rgb(0_0_0/0.28)] outline-none transition-[border-color,box-shadow,background-color] placeholder:text-muted/70 focus:border-accent/70 focus:bg-white/[0.09] focus:ring-4 focus:ring-accent/15 disabled:opacity-60'

export function Field({ label: text, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block px-1 text-[13px] text-muted">{text}</span>
      {children}
    </label>
  )
}

/* ---------- list pieces ---------- */
export function Tile({ leading, title, subtitle, trailing, onClick }: {
  leading: ReactNode; title: string; subtitle?: string; trailing?: ReactNode; onClick?: () => void
}) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag onClick={onClick} className="glass-lite animate-rise flex w-full items-center gap-3 rounded-[26px] p-3.5 text-left transition-[background-color,transform] active:bg-white/10">
      {leading}
      <div className="min-w-0 flex-1">
        <div className="truncate text-[15.5px] font-semibold">{title}</div>
        {subtitle && <div className="mt-0.5 line-clamp-2 text-[12.5px] text-muted">{subtitle}</div>}
      </div>
      {trailing && <div className="shrink-0">{trailing}</div>}
    </Tag>
  )
}

export function IconBadge({ icon, tone = 'accent' }: { icon: IconName; tone?: 'accent' | 'pos' }) {
  const cls = tone === 'pos' ? 'bg-pos/15 text-pos ring-1 ring-pos/25' : 'bg-accent/15 text-accent ring-1 ring-accent/25'
  return <div className={`grid size-[46px] shrink-0 place-items-center rounded-2xl ${cls}`}><Icon name={icon} size={22} /></div>
}

export function InfoCard({ icon, label: text, value, valueClass = '' }: { icon: IconName; label: string; value: string; valueClass?: string }) {
  return (
    <div className="glass flex min-w-0 flex-1 flex-col items-center rounded-[28px] px-3 py-4">
      <div className="grid size-10 place-items-center rounded-full bg-accent/15 text-accent ring-1 ring-accent/25"><Icon name={icon} size={21} /></div>
      <div className="mt-2 text-xs text-muted">{text}</div>
      <div className={`mt-0.5 max-w-full truncate text-[19px] font-extrabold ${valueClass}`}>{value}</div>
    </div>
  )
}

export function SectionTitle({ children, trailing }: { children: ReactNode; trailing?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center">
      <h2 className="flex-1 text-[17px] font-bold">{children}</h2>
      {trailing}
    </div>
  )
}

export function BalanceText({ paise, owedLabel = 'you are owed', oweLabel = 'you owe' }: { paise: number; owedLabel?: string; oweLabel?: string }) {
  if (paise === 0) return <span className="text-[12.5px] text-muted">settled up</span>
  const owed = paise > 0
  return (
    <div className="text-right">
      <div className="text-[11.5px] text-muted">{owed ? owedLabel : oweLabel}</div>
      <div className={`font-extrabold ${owed ? 'text-pos' : 'text-neg'}`}>{formatAbs(paise)}</div>
    </div>
  )
}
const formatAbs = (p: number) => formatPaise(Math.abs(p))

/* ---------- bottom sheet ---------- */
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
    <div className={`${open ? 'animate-fade' : 'animate-fade-out pointer-events-none'} fixed inset-0 z-50 flex items-end justify-center bg-black/45 backdrop-blur-[6px]`} onClick={onClose}>
      <div role="dialog" aria-modal onClick={(e) => e.stopPropagation()}
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

/* ---------- connectivity ---------- */
export function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true), off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off) }
  }, [])
  if (online) return null
  return (
    <div role="alert" className="glass-strong animate-rise pointer-events-none fixed left-1/2 top-[calc(0.75rem+env(safe-area-inset-top))] z-[70] w-max max-w-[calc(100%-2rem)] -translate-x-1/2 rounded-full px-4 py-2 text-center text-[13px] font-semibold text-neg">
      You're offline · changes won't save
    </div>
  )
}

/* ---------- async helper ---------- */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>({ loading: true })
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, error: undefined, loading: true }))
    load().then((data) => live && setState({ data, loading: false }), (error) => live && setState({ error, loading: false }))
    return () => { live = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])
  return { ...state, reload: () => setTick((t) => t + 1) }
}

export function AuthPage({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Screen className="pt-safe pb-safe">
      <div className="flex flex-1 flex-col justify-center px-5 py-8">
        <div className="btn-accent mx-auto grid size-[76px] place-items-center rounded-[26px] shadow-[0_14px_36px_-12px_rgb(250_210_70/0.45)]"><Icon name="split" size={38} /></div>
        <h1 className="mt-6 text-center text-[28px] font-extrabold">{title}</h1>
        {subtitle && <p className="mt-2 text-center text-muted">{subtitle}</p>}
        <div className="glass mt-7 space-y-5 rounded-[32px] p-5">{children}</div>
      </div>
    </Screen>
  )
}
