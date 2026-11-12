import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { activityView, groupByDay } from '../lib/activity'
import { useActivity } from '../lib/queries'
import { isFiltering, NO_FILTER } from '../lib/searchFilter'
import { Dashed, EmptyState, ErrorRetry, IconBadge, PageTitle, Screen, SearchBar, Skeleton, Tile, useDebounced } from '../ui'

export function ActivityScreen() {
  const me = useAuth().user!
  const nav = useNavigate()
  const [filter, setFilter] = useState(NO_FILTER)
  const searching = isFiltering(filter)
  // the box updates at once; the server is asked once typing pauses
  const sent = useDebounced(filter)
  const { data, error, isPending: loading, isPlaceholderData, refetch, hasNextPage, fetchNextPage, isFetchingNextPage } = useActivity(sent)
  const reload = () => { void refetch() }
  const items = data?.pages.flatMap((p) => p.items)
  return (
    <Screen className="pt-safe pb-safe" onRefresh={reload}>
      <div className="space-y-5 px-5 pb-32 pt-4">
        <PageTitle title="Activity" subtitle="Expenses and payments that involve you, across all your groups." />
        {(searching || (items && items.length > 0)) && <SearchBar value={filter} onChange={setFilter} placeholder="Search your expenses" />}
        {loading && !items ? <div className="space-y-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[78px] rounded-[22px]" />)}</div>
          : error && !items ? <ErrorRetry error={error} onRetry={reload} />
          : items && items.length === 0 ? (
            <Dashed>{isFiltering(sent)
              ? <EmptyState icon="search" title="No matches">Nothing you are part of matches that. Payments are not searched.</EmptyState>
              : <EmptyState icon="activity" title="Nothing yet">Expenses and payments that involve you will show up here.</EmptyState>}</Dashed>
          ) : (
            <div className={`space-y-5 transition-opacity ${isPlaceholderData ? 'opacity-60' : ''}`}>
              {items && groupByDay(items).map((d) => (
                <section key={d.day}>
                  <h3 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-muted">{d.day}</h3>
                  <div className="stagger space-y-3">
                    {d.items.map((i) => {
                      const v = activityView(i, me)
                      return (
                        <Tile key={`${i.type}${i.id}`} leading={<IconBadge icon={v.icon} tone={i.type === 'PAYMENT' ? 'pos' : 'accent'} />}
                          title={v.title} subtitle={v.subtitle}
                          trailing={<div className={`whitespace-pre-line text-right text-[13px] font-bold ${v.noteClass}`}>{v.note}</div>}
                          onClick={() => nav(`/groups/${i.groupId}`)} />
                      )
                    })}
                  </div>
                </section>
              ))}
              {hasNextPage && (
                <button type="button" onClick={() => { void fetchNextPage() }} disabled={isFetchingNextPage}
                  className="glass-lite w-full rounded-full py-3.5 font-semibold disabled:opacity-60">
                  {isFetchingNextPage ? 'Loading…' : 'Load older'}
                </button>
              )}
            </div>
          )}
      </div>
    </Screen>
  )
}
