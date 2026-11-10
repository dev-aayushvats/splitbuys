import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useFriends } from '../lib/queries'
import { formatPaise } from '../lib/money'
import { label, type Friend } from '../lib/types'
import { Avatar, BalanceText, Dashed, EmptyState, ErrorRetry, Icon, PageTitle, Screen, Skeleton, tap } from '../ui'

export function FriendsScreen() {
  const { data: friends, error, isPending: loading, refetch } = useFriends()
  const reload = () => { void refetch() }
  return (
    <Screen className="pt-safe pb-safe" onRefresh={reload}>
      <div className="space-y-6 px-5 pb-32 pt-4">
        <PageTitle title="Friends" subtitle="People you share a group with, and what you owe each other across those groups." />
        {loading && !friends ? <div className="space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-[78px] rounded-[22px]" />)}</div>
          : error && !friends ? <ErrorRetry error={error} onRetry={reload} />
          : friends && friends.length === 0 ? (
            <Dashed><EmptyState icon="friends" title="No friends yet">Anyone you share a group with will show up here.</EmptyState></Dashed>
          ) : (
            <div className="stagger space-y-3">{friends?.map((f) => <FriendRow key={f.user.id} friend={f} />)}</div>
          )}
      </div>
    </Screen>
  )
}

/** One person; tap to see the groups behind the number, and tap a group to open it. */
function FriendRow({ friend }: { friend: Friend }) {
  const nav = useNavigate()
  const [open, setOpen] = useState(false)
  const n = friend.groups.length
  return (
    <div className="glass-lite animate-rise overflow-hidden rounded-[26px]">
      <button type="button" aria-expanded={open} onClick={() => { tap(); setOpen((o) => !o) }}
        className="flex w-full items-center gap-3 p-3.5 text-left transition-colors active:bg-white/10">
        <Avatar user={friend.user} size={46} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-[15.5px] font-semibold">{label(friend.user)}</div>
          <div className="mt-0.5 text-[12.5px] text-muted">{n} shared group{n === 1 ? '' : 's'}</div>
        </div>
        <div className="shrink-0"><BalanceText paise={friend.balancePaise} owedLabel="owes you" oweLabel="you owe" /></div>
        <Icon name="back" size={18} className={`shrink-0 text-muted transition-transform duration-200 ${open ? '-rotate-90' : '-rotate-180'}`} />
      </button>
      {open && (
        <ul className="animate-rise space-y-1 border-t border-white/10 px-3.5 pb-3 pt-2">
          {friend.groups.map((g) => (
            <li key={g.id}>
              <button type="button" onClick={() => nav(`/groups/${g.id}`)}
                className="flex w-full items-center gap-3 rounded-2xl px-2 py-2.5 text-left transition-colors active:bg-white/10">
                <span className="min-w-0 flex-1 truncate text-[14px]">{g.name}</span>
                <span className={`shrink-0 text-[13.5px] font-semibold ${g.balancePaise > 0 ? 'text-pos' : g.balancePaise < 0 ? 'text-neg' : 'text-muted'}`}>
                  {g.balancePaise === 0 ? 'settled up' : `${g.balancePaise > 0 ? 'owes you ' : 'you owe '}${formatPaise(Math.abs(g.balancePaise))}`}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
