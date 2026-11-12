import type { ReactNode } from 'react'
import { formatPaise } from '../lib/money'
import { type IconName, Icon } from './icons'

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

export function IconBadge({ icon, tone = 'accent' }: { icon: IconName; tone?: 'accent' | 'pos' | 'neg' }) {
  const cls = tone === 'pos' ? 'bg-pos/15 text-pos ring-1 ring-pos/25' : tone === 'neg' ? 'bg-neg/15 text-neg ring-1 ring-neg/25' : 'bg-accent/15 text-accent ring-1 ring-accent/25'
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
