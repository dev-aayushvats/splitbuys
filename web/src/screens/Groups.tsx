import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useGroups } from '../lib/queries'
import { formatPaise } from '../lib/money'
import { headline, owedShare, summarize } from '../lib/overview'
import { label, type GroupSummary } from '../lib/types'
import { NewGroupSheet } from './NewGroup'
import {
  BalanceText, Dashed, EmptyState, ErrorRetry, Icon, IconBadge, RoundButton, Screen, SectionTitle, Skeleton, Tile,
} from '../ui'

export function GroupsScreen() {
  const { user } = useAuth()
  const nav = useNavigate()
  const { data: groups, error, isPending: loading, refetch } = useGroups()
  const reload = () => { void refetch() }
  const [creating, setCreating] = useState(false)
  const me = user!

  return (
    <Screen className="pt-safe pb-safe" onRefresh={reload}>
      <div className="space-y-6 px-5 pb-32 pt-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="text-[13px] text-muted">Welcome back</div>
            <div className="truncate text-2xl font-extrabold">{label(me)}</div>
          </div>
        </div>

        <Overview groups={groups} />

        <section>
          <SectionTitle trailing={
            <div className="flex items-center gap-3">
              {groups && <span className="text-muted">{groups.length}</span>}
              <RoundButton label="New group" onClick={() => setCreating(true)}><Icon name="plus" size={22} /></RoundButton>
            </div>
          }>Your groups</SectionTitle>
          {loading && !groups ? <div className="space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-[78px] rounded-[22px]" />)}</div>
            : error && !groups ? <ErrorRetry error={error} onRetry={reload} />
            : groups && groups.length === 0 ? (
              <Dashed><EmptyState icon="groups" title="No groups yet">Create one, or open an invite link from a friend.</EmptyState></Dashed>
            ) : (
              <div className="stagger space-y-3">
                {groups?.map((g) => (
                  <Tile key={g.id} leading={<IconBadge icon="groups" />} title={g.name}
                    subtitle={`${g.memberCount} member${g.memberCount === 1 ? '' : 's'}`}
                    trailing={<BalanceText paise={g.myBalancePaise} />} onClick={() => nav(`/groups/${g.id}`)} />
                ))}
              </div>
            )}
        </section>
      </div>

      <NewGroupSheet open={creating} onClose={() => setCreating(false)} />
    </Screen>
  )
}

/**
 * One headline figure (what you are owed or owe overall), a bar that shows how it splits, and the two sides with the
 * number of groups behind each. The net is the only number shown twice-removed: the sides are its parts, not copies.
 */
export function Overview({ groups }: { groups: GroupSummary[] | undefined }) {
  if (!groups) return <Skeleton className="h-[184px] rounded-[32px]" />
  const o = summarize(groups)
  const tone = o.net > 0 ? 'text-pos' : o.net < 0 ? 'text-neg' : 'text-ink'
  const share = owedShare(o)
  const plural = (n: number) => `${n} group${n === 1 ? '' : 's'}`
  return (
    <div className="glass overflow-hidden rounded-[32px] p-5">
      <div className="flex items-center gap-2 text-[13px] font-semibold text-muted">
        <span className="grid size-7 place-items-center rounded-full bg-accent/15 text-accent ring-1 ring-accent/25"><Icon name="wallet" size={15} /></span>
        Overall balance
      </div>
      <div className={`mt-3 truncate text-[34px] font-extrabold leading-none ${tone}`}>{formatPaise(Math.abs(o.net))}</div>
      <div className="mt-1.5 text-[13px] text-muted">{headline(o, groups.length)}</div>

      {o.owed + o.owe > 0 && (
        <>
          <div role="img" aria-label={`${share.toFixed(0)}% owed to you, ${(100 - share).toFixed(0)}% you owe`}
            className="mt-4 flex h-2 gap-0.5 overflow-hidden rounded-full bg-white/10">
            <div className="rounded-full bg-pos" style={{ width: `${share}%` }} />
            <div className="rounded-full bg-neg" style={{ width: `${100 - share}%` }} />
          </div>
          <dl className="mt-3.5 grid grid-cols-2 gap-3">
            <Side label="You're owed" tone="bg-pos" value={o.owed} groups={o.owedGroups > 0 ? `in ${plural(o.owedGroups)}` : 'nothing pending'} />
            <Side label="You owe" tone="bg-neg" value={o.owe} groups={o.oweGroups > 0 ? `in ${plural(o.oweGroups)}` : 'nothing pending'} />
          </dl>
        </>
      )}
    </div>
  )
}

function Side({ label, tone, value, groups }: { label: string; tone: string; value: number; groups: string }) {
  return (
    <div className="min-w-0 rounded-2xl bg-white/[0.05] px-3.5 py-2.5 ring-1 ring-white/10">
      <dt className="flex items-center gap-1.5 text-[12px] text-muted"><span className={`size-2 rounded-full ${tone}`} />{label}</dt>
      <dd className="mt-1 truncate text-[17px] font-bold">{formatPaise(value)}</dd>
      <dd className="text-[11.5px] text-muted">{groups}</dd>
    </div>
  )
}
