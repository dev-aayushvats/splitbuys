import { useState } from 'react'
import { categoryEmoji, categoryLabel } from '../lib/categories'
import { bars, change, coverage, perDay, proportions } from '../lib/insights'
import { formatPaise } from '../lib/money'
import { useSpendingInsights, useSpendingMonth } from '../lib/queries'
import { label } from '../lib/types'

/** Profile card: this month's spending, six months as bars, and what the selected month went on. Absent until there is spending. */
export function InsightsCard() {
  const { data } = useSpendingInsights()
  const [picked, setPicked] = useState<string | null>(null)
  const [more, setMore] = useState(false)
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

      <button type="button" onClick={() => setMore((v) => !v)} aria-expanded={more}
        className="w-full text-center text-[13px] font-semibold text-accent-ink">{more ? 'Hide details' : 'Details for this month'}</button>
      {more && <MonthDetailView month={selected.month} />}
    </section>
  )
}

/** The picked month in more detail: paid out against own share, a daily average, and where and with whom it went. */
function MonthDetailView({ month }: { month: string }) {
  const { data, isPending, error } = useSpendingMonth(month, true)
  if (isPending) return <div className="h-24 animate-pulse rounded-2xl bg-white/[0.06]" />
  if (error || !data) return <p className="text-[13px] text-muted">Could not load the details.</p>
  const cover = coverage(data)
  const groupBars = proportions(data.groups)
  const peopleBars = proportions(data.people)
  return (
    <div className="space-y-4 border-t border-white/10 pt-3">
      <dl className="grid grid-cols-3 gap-2 text-center">
        <Stat label="Your share" value={formatPaise(data.sharePaise)} />
        <Stat label="You paid out" value={formatPaise(data.paidPaise)} />
        <Stat label="Per day" value={formatPaise(perDay(data))} />
      </dl>
      {cover.kind !== 'even' && (
        <p className="text-[13px] text-muted">
          {cover.kind === 'covered' ? `You covered ${formatPaise(cover.paise)} more than your share.` : `Others paid ${formatPaise(cover.paise)} of your share.`}
        </p>
      )}
      {data.groups.length > 0 && (
        <Ranked title="Where it went" rows={data.groups.slice(0, 3).map((g, i) => ({ key: g.groupId, name: g.name, paise: g.paise, width: groupBars[i] }))} />
      )}
      {data.people.length > 0 && (
        <Ranked title="Spent most together with" rows={data.people.slice(0, 3).map((p, i) => ({ key: p.user.id, name: label(p.user), paise: p.paise, width: peopleBars[i] }))} />
      )}
      <p className="text-[12px] text-muted">{data.expenseCount} expense{data.expenseCount === 1 ? '' : 's'} you shared in.</p>
    </div>
  )
}

function Stat({ label: text, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-2xl bg-white/[0.05] px-2 py-2 ring-1 ring-white/10">
      <dt className="text-[11px] text-muted">{text}</dt>
      <dd className="truncate text-[13.5px] font-bold">{value}</dd>
    </div>
  )
}

function Ranked({ title, rows }: { title: string; rows: { key: number; name: string; paise: number; width: number }[] }) {
  return (
    <div>
      <div className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted">{title}</div>
      <div className="space-y-2">
        {rows.map((r) => (
          <div key={r.key}>
            <div className="flex items-center justify-between gap-3 text-[14px]">
              <span className="min-w-0 truncate">{r.name}</span>
              <span className="shrink-0 font-semibold">{formatPaise(r.paise)}</span>
            </div>
            <div className="mt-1 h-1.5 rounded-full bg-white/10"><div className="h-full rounded-full bg-accent" style={{ width: `${Math.max(r.width * 100, 4)}%` }} /></div>
          </div>
        ))}
      </div>
    </div>
  )
}
