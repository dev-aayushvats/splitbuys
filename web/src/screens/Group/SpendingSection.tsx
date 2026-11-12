import { categoryEmoji, categoryLabel } from '../../lib/categories'
import { formatPaise } from '../../lib/money'
import { useSpending } from '../../lib/queries'
import { Dashed, SectionTitle } from '../../ui'

/** Where the group's money went: a bar per category, biggest first, with the signed-in user's own share of each. */
export function SpendingSection({ gid }: { gid: number }) {
  const { data } = useSpending(gid)
  if (!data || data.categories.length === 0) return null
  const biggest = data.categories[0].totalPaise
  return (
    <section>
      <SectionTitle>Where the money went</SectionTitle>
      <Dashed className="space-y-4 p-4">
        {data.categories.map((c) => (
          <div key={c.category}>
            <div className="flex items-baseline justify-between gap-3">
              <span className="font-semibold">{categoryEmoji(c.category)} {categoryLabel(c.category)}</span>
              <span className="font-bold">{formatPaise(c.totalPaise)}</span>
            </div>
            <div role="img" aria-label={`${categoryLabel(c.category)}: ${formatPaise(c.totalPaise)}`} className="mt-1.5 h-2 overflow-hidden rounded-full bg-white/10">
              <div className="h-full rounded-full bg-accent" style={{ width: `${Math.max(4, (c.totalPaise / biggest) * 100)}%` }} />
            </div>
            <div className="mt-1 text-[12px] text-muted">{c.count} expense{c.count === 1 ? '' : 's'} · your share {formatPaise(c.mySharePaise)}</div>
          </div>
        ))}
        <div className="border-t border-white/10 pt-3 text-[13px] text-muted">
          Your share of everything: <span className="font-bold text-ink">{formatPaise(data.mySharePaise)}</span>
        </div>
      </Dashed>
    </section>
  )
}
