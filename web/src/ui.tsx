import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
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
        <div className="pointer-events-none fixed inset-x-0 bottom-24 z-[60] flex justify-center px-6">
          <div role="status" className="animate-rise pointer-events-auto rounded-2xl border border-line bg-high px-4 py-3 text-sm shadow-xl">
            {msg}
          </div>
        </div>
      )}
      <Sheet open={!!ask} onClose={() => close(false)} title={ask?.title}>
        <div className="flex gap-3">
          <button type="button" className="h-13 flex-1 rounded-full border border-line py-3.5 font-semibold" onClick={() => close(false)}>Cancel</button>
          <button type="button" className="h-13 flex-1 rounded-full bg-neg py-3.5 font-bold text-on-accent" onClick={() => close(true)}>{ask?.action}</button>
        </div>
      </Sheet>
    </FeedbackCtx.Provider>
  )
}

/* ---------- layout ---------- */
export function Screen({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`animate-fade mx-auto flex min-h-dvh w-full max-w-[480px] flex-col bg-bg ${className}`}>{children}</div>
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

export const Dashed = ({ children, className = 'p-4' }: { children: ReactNode; className?: string }) => (
  <div className={`rounded-3xl border border-dashed border-dash ${className}`}>{children}</div>
)

export function RoundButton({ children, onClick, label: aria, disabled }: { children: ReactNode; onClick?: () => void; label: string; disabled?: boolean }) {
  return (
    <button type="button" aria-label={aria} onClick={onClick} disabled={disabled}
      className="grid size-11 place-items-center rounded-full border border-line bg-surface disabled:text-muted">
      {children}
    </button>
  )
}

export function TopBar({ title, showBack = true, action }: { title: string; showBack?: boolean; action?: ReactNode }) {
  const nav = useNavigate()
  return (
    <header className="pt-safe sticky top-0 z-30 bg-bg/90 backdrop-blur">
      <div className="flex items-center gap-2 px-5 pb-2 pt-3">
        <div className="w-11">
          {showBack && (
            <RoundButton label="Back" onClick={() => (window.history.state?.idx > 0 ? nav(-1) : nav('/', { replace: true }))}>
              <Icon name="back" />
            </RoundButton>
          )}
        </div>
        <h1 className="min-w-0 flex-1 truncate text-center text-xl font-bold">{title}</h1>
        <div className="flex w-11 justify-end">{action}</div>
      </div>
    </header>
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
    <div className="grid shrink-0 place-items-center rounded-full font-extrabold text-on-accent"
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
  const ring = 'absolute rounded-full bg-bg p-0.5'
  return (
    <div className="relative shrink-0" style={{ width: size + 4 + step * (count - 1), height: size + 4 }}>
      {shown.map((u, i) => (
        <div key={u.id} className={ring} style={{ left: i * step }}><Avatar user={u} size={size} /></div>
      ))}
      {extra > 0 && (
        <div className={ring} style={{ left: shown.length * step }}>
          <div className="grid place-items-center rounded-full bg-high font-bold" style={{ width: size, height: size, fontSize: size * 0.34 }}>+{extra}</div>
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
      className="flex h-15 w-full items-center rounded-full bg-gradient-to-r from-accent to-accent-deep pl-1.5 pr-14 text-base font-bold text-on-accent disabled:opacity-45">
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
      className={`inline-flex h-11 items-center justify-center gap-2 rounded-full bg-accent px-5 text-sm font-bold text-on-accent disabled:bg-high disabled:text-muted ${className}`}>
      {children}
    </button>
  )
}

export function PillTabs({ labels, index, onChange }: { labels: string[]; index: number; onChange: (i: number) => void }) {
  return (
    <div role="tablist" className="flex rounded-full border border-line bg-surface p-1">
      {labels.map((l, i) => (
        <button type="button" key={l} role="tab" aria-selected={i === index} onClick={() => onChange(i)}
          className={`flex-1 rounded-full py-2.5 text-[13.5px] font-bold transition-colors ${i === index ? 'bg-accent text-on-accent' : 'text-muted'}`}>
          {l}
        </button>
      ))}
    </div>
  )
}

export const inputCls =
  'w-full rounded-2xl border border-line bg-surface px-4 py-4 outline-none placeholder:text-muted focus:border-accent focus:ring-1 focus:ring-accent disabled:opacity-60'

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
    <Tag onClick={onClick} className="animate-rise flex w-full items-center gap-3 rounded-[22px] border border-line/60 bg-surface p-3.5 text-left transition-colors active:bg-high">
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
  const cls = tone === 'pos' ? 'bg-pos/15 text-pos' : 'bg-accent/15 text-accent'
  return <div className={`grid size-[46px] shrink-0 place-items-center rounded-2xl ${cls}`}><Icon name={icon} size={22} /></div>
}

export function InfoCard({ icon, label: text, value, valueClass = '' }: { icon: IconName; label: string; value: string; valueClass?: string }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col items-center rounded-3xl border-[1.5px] border-dash bg-gradient-to-br from-high to-surface px-3 py-4">
      <div className="grid size-10 place-items-center rounded-full bg-accent/15 text-accent"><Icon name={icon} size={21} /></div>
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
  return (
    <div className="animate-fade fixed inset-0 z-50 flex items-end justify-center bg-black/60" onClick={onClose}>
      <div role="dialog" aria-modal onClick={(e) => e.stopPropagation()}
        className="animate-sheet pb-safe w-full max-w-[480px] rounded-t-[28px] border-t border-line bg-high px-5 pb-5 pt-3">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-line" />
        {title && <h2 className="mb-4 whitespace-pre-line text-lg font-bold">{title}</h2>}
        {children}
      </div>
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
      <div className="flex flex-1 flex-col justify-center px-6 py-8">
        <div className="mx-auto grid size-[72px] place-items-center rounded-full bg-accent text-on-accent"><Icon name="split" size={36} /></div>
        <h1 className="mt-6 text-center text-[26px] font-extrabold">{title}</h1>
        {subtitle && <p className="mt-2 text-center text-muted">{subtitle}</p>}
        <div className="mt-7 space-y-5">{children}</div>
      </div>
    </Screen>
  )
}
