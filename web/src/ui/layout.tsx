import { useEffect, useRef, useState, type ReactNode } from 'react'
import { type IconName, Icon } from './icons'

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

/**
 * The page behind everything: a still, muted slate-navy gradient with a soft cool light at the top and a little grain.
 * It stays quiet on purpose; the grain and light give the glass panes something to frost over.
 */
export function Backdrop() {
  return (
    <div aria-hidden className="app-backdrop pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="orb orb-1" /><div className="orb orb-2" /><div className="orb orb-3" />
      <svg aria-hidden="true" className="absolute inset-0 size-full opacity-[0.06] mix-blend-overlay">
        <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" stitchTiles="stitch" /></filter>
        <rect width="100%" height="100%" filter="url(#grain)" />
      </svg>
    </div>
  )
}

export function AuthPage({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Screen className="pt-safe pb-safe">
      <div className="flex flex-1 flex-col justify-center px-5 py-8">
        <img src="/pwa-192.png" alt="" width={88} height={88} className="mx-auto size-[88px] drop-shadow-[0_14px_24px_rgb(255_140_15/0.3)]" />
        <h1 className="mt-6 text-center text-[28px] font-extrabold">{title}</h1>
        {subtitle && <p className="mt-2 text-center text-muted">{subtitle}</p>}
        <div className="glass mt-7 space-y-5 rounded-[32px] p-5">{children}</div>
      </div>
    </Screen>
  )
}
