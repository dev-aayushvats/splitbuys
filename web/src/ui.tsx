import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
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
        <div className="pointer-events-none fixed inset-x-0 bottom-28 z-[60] flex justify-center px-6">
          <div role="status" className="glass-strong animate-rise pointer-events-auto rounded-full px-5 py-3 text-sm font-medium">
            {msg}
          </div>
        </div>
      )}
      <Sheet open={!!ask} onClose={() => close(false)} title={ask?.title}>
        <div className="flex gap-3">
          <button type="button" className="h-13 flex-1 rounded-full border border-line bg-white/[0.06] py-3.5 font-semibold" onClick={() => close(false)}>Cancel</button>
          <button type="button" className="h-13 flex-1 rounded-full bg-neg py-3.5 font-bold text-on-accent shadow-[0_10px_28px_-8px_rgb(255_90_90/0.6),inset_0_1px_0_rgb(255_255_255/0.4)]" onClick={() => close(true)}>{ask?.action}</button>
        </div>
      </Sheet>
    </FeedbackCtx.Provider>
  )
}

/** Light haptic tick where the browser supports it (Android); a no-op elsewhere. */
export const tap = () => { try { navigator.vibrate?.(8) } catch { /* unsupported */ } }

export const Skeleton = ({ className = '' }: { className?: string }) => (
  <div aria-hidden className={`animate-pulse rounded-2xl bg-white/[0.07] ${className}`} />
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
    <div className={`animate-fade mx-auto flex min-h-dvh w-full max-w-[480px] flex-col ${className}`}>
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
    <div aria-hidden className="pointer-events-none fixed inset-x-0 top-0 z-40 flex justify-center"
      style={{ transform: `translateY(${pull - 40}px)`, opacity: progress, transition: start.current === null ? 'transform .25s, opacity .25s' : 'none' }}>
      <div className="glass-strong mt-[env(safe-area-inset-top)] grid size-9 place-items-center rounded-full">
        {busy ? <Spinner className="size-4" /> : <Icon name="arrow" size={16} className="rotate-90 text-accent" />}
      </div>
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

/** A frosted pane. (Named for the dashed outline it replaced; call sites pass padding/radius.) */
export const Dashed = ({ children, className = 'p-4' }: { children: ReactNode; className?: string }) => (
  <div className={`glass-lite rounded-[28px] ${className}`}>{children}</div>
)

export function RoundButton({ children, onClick, label: aria, disabled }: { children: ReactNode; onClick?: () => void; label: string; disabled?: boolean }) {
  return (
    <button type="button" aria-label={aria} onClick={onClick} disabled={disabled}
      className="grid size-10 place-items-center rounded-full bg-white/10 transition-colors active:bg-white/20 disabled:text-muted">
      {children}
    </button>
  )
}

export function TopBar({ title, showBack = true, action }: { title: string; showBack?: boolean; action?: ReactNode }) {
  const nav = useNavigate()
  return (
    <header className="pt-safe sticky top-0 z-30">
      {/* the bar floats: inset from the edges, fully rounded, with the page scrolling beneath its frosted glass */}
      <div className="glass-strong mx-4 mt-3 flex items-center gap-2 rounded-full p-1.5">
        <div className="w-10">
          {showBack && (
            <RoundButton label="Back" onClick={() => (window.history.state?.idx > 0 ? nav(-1) : nav('/', { replace: true }))}>
              <Icon name="back" />
            </RoundButton>
          )}
        </div>
        <h1 className="min-w-0 flex-1 truncate text-center text-[17px] font-semibold">{title}</h1>
        <div className="flex w-10 justify-end">{action}</div>
      </div>
    </header>
  )
}

/** Ambient light behind the whole app: slowly drifting colour orbs that the glass panes blur and refract. */
export function Backdrop() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 -z-10 overflow-hidden bg-gradient-to-b from-[#0b0c18] via-[#080913] to-[#06070c]">
      <div className="animate-drift-a absolute -left-28 -top-24 size-[26rem] rounded-full bg-[radial-gradient(circle,rgb(255_196_48/0.34),transparent_65%)]" />
      <div className="animate-drift-b absolute -right-40 top-[28%] size-[30rem] rounded-full bg-[radial-gradient(circle,rgb(124_92_255/0.38),transparent_65%)]" />
      <div className="animate-drift-c absolute -bottom-32 left-[-4rem] size-[28rem] rounded-full bg-[radial-gradient(circle,rgb(34_211_190/0.26),transparent_65%)]" />
    </div>
  )
}

/** Floating bottom navigation: a frosted pill with a sliding highlight. */
export function Dock({ items, index, onChange }: { items: { label: string; icon: IconName }[]; index: number; onChange: (i: number) => void }) {
  const W = 100
  return (
    <nav className="pointer-events-none fixed inset-x-0 bottom-0 z-30 mx-auto flex max-w-[480px] justify-center pb-[calc(1rem+env(safe-area-inset-bottom))]">
      <div role="tablist" className="glass-strong pointer-events-auto relative flex rounded-full p-1.5">
        <div aria-hidden className="absolute inset-y-1.5 rounded-full bg-white/[0.16] shadow-[inset_0_1px_0_rgb(255_255_255/0.3)] transition-[left] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
          style={{ width: W, left: 6 + index * W }} />
        {items.map((it, i) => (
          <button type="button" key={it.label} role="tab" aria-selected={i === index} onClick={() => { tap(); onChange(i) }}
            className={`relative z-10 flex h-14 flex-col items-center justify-center gap-0.5 rounded-full text-[11px] font-semibold transition-colors duration-200 ${i === index ? 'text-accent' : 'text-muted'}`}
            style={{ width: W }}>
            <Icon name={it.icon} size={21} />
            {it.label}
          </button>
        ))}
      </div>
    </nav>
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
  const ring = 'absolute rounded-full bg-[#0b0c18] p-0.5'
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

export function PillTabs({ labels, index, onChange }: { labels: string[]; index: number; onChange: (i: number) => void }) {
  const n = labels.length
  return (
    <div role="tablist" className="glass-lite relative flex rounded-full p-1">
      <div aria-hidden className="btn-accent absolute inset-y-1 rounded-full transition-[left] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
        style={{ width: `calc((100% - 8px) / ${n})`, left: `calc(4px + ${index} * (100% - 8px) / ${n})` }} />
      {labels.map((l, i) => (
        <button type="button" key={l} role="tab" aria-selected={i === index} onClick={() => { tap(); onChange(i) }}
          className={`relative z-10 flex-1 rounded-full py-2.5 text-[13.5px] font-bold transition-colors duration-200 ${i === index ? 'text-on-accent' : 'text-muted'}`}>
          {l}
        </button>
      ))}
    </div>
  )
}

export const inputCls =
  'w-full rounded-2xl border border-line bg-white/[0.06] px-4 py-4 shadow-[inset_0_2px_8px_rgb(0_0_0/0.28)] outline-none transition-[border-color,box-shadow,background-color] placeholder:text-muted/70 focus:border-accent/70 focus:bg-white/[0.09] focus:ring-4 focus:ring-accent/15 disabled:opacity-60'

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
export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title?: string; children: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])
  if (!open) return null
  return createPortal(
    <div className="animate-fade fixed inset-0 z-50 flex items-end justify-center bg-black/40 backdrop-blur-[3px]" onClick={onClose}>
      <div role="dialog" aria-modal onClick={(e) => e.stopPropagation()}
        className="glass-strong animate-sheet mb-[max(0.75rem,env(safe-area-inset-bottom))] w-[calc(100%-1.5rem)] max-w-[456px] rounded-[32px] px-5 pb-5 pt-3">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-white/25" />
        {title && <h2 className="mb-4 whitespace-pre-line text-lg font-bold">{title}</h2>}
        {children}
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
    <div role="alert" className="pt-safe animate-rise pointer-events-none fixed inset-x-0 top-0 z-[70]">
      <div className="glass-strong mx-auto mt-3 w-fit max-w-[calc(100%-2rem)] rounded-full px-4 py-2 text-center text-[13px] font-semibold text-neg">You're offline · changes won't save</div>
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
        <div className="btn-accent mx-auto grid size-[76px] place-items-center rounded-[26px] shadow-[0_18px_50px_-10px_rgb(255_200_50/0.55)]"><Icon name="split" size={38} /></div>
        <h1 className="mt-6 text-center text-[28px] font-extrabold">{title}</h1>
        {subtitle && <p className="mt-2 text-center text-muted">{subtitle}</p>}
        <div className="glass mt-7 space-y-5 rounded-[32px] p-5">{children}</div>
      </div>
    </Screen>
  )
}
