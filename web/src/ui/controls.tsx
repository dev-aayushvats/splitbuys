import type { ReactNode } from 'react'
import { Icon } from './icons'
import { Spinner } from './layout'

export function RoundButton({ children, onClick, label: aria, disabled }: { children: ReactNode; onClick?: () => void; label: string; disabled?: boolean }) {
  return (
    <button type="button" aria-label={aria} onClick={onClick} disabled={disabled}
      className="glass-btn grid size-11 place-items-center rounded-full disabled:text-muted">
      {children}
    </button>
  )
}

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
    // biome-ignore lint/a11y/noLabelWithoutControl: the input is passed in as children, inside this label, which associates them
    <label className="block">
      <span className="mb-1.5 block px-1 text-[13px] text-muted">{text}</span>
      {children}
    </label>
  )
}
