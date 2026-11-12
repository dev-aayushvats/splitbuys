import { useState } from 'react'
import { categoryEmoji, categoryLabel } from '../lib/categories'
import { bars, change } from '../lib/insights'
import { formatPaise } from '../lib/money'
import { useSpendingInsights } from '../lib/queries'

/** Profile card: this month's spending, six months as bars, and what the selected month went on. Absent until there is spending. */
export function InsightsCard() {
  const { data } = useSpendingInsights()
  const [picked, setPicked] = useState<string | null>(null)
  const chart = data ? bars(data) : null
  if (!data || !chart) return null
  const current = data.months.at(-1)!
  const selected = data.months.find((m) => m.month === picked) ?? current
  const vs = change(data.months)
  const top = selected.categories.slice(0, 4)

  return (
    <section className="glass-lite space-y-4 rounded-[24px] p-4">
      <div className="flex items-end justify-between gap-3">
        <div>
          <div className="text-[13px] text-muted">Your spending this month</div>
          <div className="text-[28px] font-extrabold leading-tight">{formatPaise(current.totalPaise)}</div>
        </div>
        {vs && <div className={`pb-1 text-[13px] font-semibold ${vs.up ? 'text-neg' : 'text-pos'}`}>{vs.up ? '▲' : '▼'} {vs.percent}% vs last month</div>}
      </div>

      <div className="flex h-24 items-end gap-2">
        {chart.map((b) => (
          <button key={b.month} type="button" onClick={() => setPicked(b.month)} aria-pressed={b.month === selected.month}
            aria-label={`${b.label}: ${formatPaise(b.totalPaise)}`} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1.5">
            {/* the bar's height is a share of this track, so a tall month cannot be squeezed by the label below it */}
            <span className="flex min-h-0 w-full flex-1 items-end">
              <span className={`w-full rounded-t-lg ${b.month === selected.month ? 'bg-accent' : 'bg-white/20'}`} style={{ height: `${Math.max(b.height * 100, b.height > 0 ? 6 : 2)}%` }} />
            </span>
            <span className={`text-[11px] ${b.month === selected.month ? 'font-bold' : 'text-muted'}`}>{b.label}</span>
          </button>
        ))}
      </div>

      <div className="space-y-1.5">
        {top.length === 0 ? <p className="text-[13px] text-muted">Nothing in this month.</p> : top.map((c) => (
          <div key={c.category} className="flex items-center justify-between text-[14px]">
            <span>{categoryEmoji(c.category)} {categoryLabel(c.category)}</span>
            <span className="font-semibold">{formatPaise(c.paise)}</span>
          </div>
        ))}
        {selected.categories.length > top.length && (
          <div className="text-[12px] text-muted">+ {selected.categories.length - top.length} more</div>
        )}
      </div>
    </section>
  )
}
