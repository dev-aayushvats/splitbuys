import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatPaise } from '../lib/money'
import { label, type GroupSummary } from '../lib/types'
import {
  Avatar, BalanceText, Dashed, EmptyState, ErrorRetry, Field, Icon, IconBadge, PillButton, Screen, SectionTitle,
  Sheet, Skeleton, Tile, inputCls, useAsync, useFeedback,
} from '../ui'

export function GroupsScreen() {
  const { user } = useAuth()
  const nav = useNavigate()
  const { toast } = useFeedback()
  const { data: groups, error, loading, reload } = useAsync(() => api.groups(), [])
  const [creating, setCreating] = useState(false)
  const me = user!

  return (
    <Screen className="pt-safe pb-safe" onRefresh={reload}>
      <div className="space-y-6 px-5 pb-10 pt-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="text-[13px] text-muted">Welcome back</div>
            <div className="truncate text-2xl font-extrabold">{label(me)}</div>
          </div>
          <button aria-label="Profile" onClick={() => nav('/profile')}><Avatar user={me} size={46} /></button>
        </div>

        <Overview groups={groups ?? []} onNewGroup={() => setCreating(true)} />

        <section>
          <SectionTitle trailing={groups && <span className="text-muted">{groups.length}</span>}>Your groups</SectionTitle>
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

      <NewGroupSheet open={creating} onClose={() => setCreating(false)} onCreate={async (name) => {
        try {
          const g = await api.createGroup(name)
          setCreating(false)
          nav(`/groups/${g.id}`)
        } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') }
      }} />

    </Screen>
  )
}

function Overview({ groups, onNewGroup }: { groups: GroupSummary[]; onNewGroup: () => void }) {
  const owed = groups.filter((g) => g.myBalancePaise > 0).reduce((s, g) => s + g.myBalancePaise, 0)
  const owe = groups.filter((g) => g.myBalancePaise < 0).reduce((s, g) => s - g.myBalancePaise, 0)
  const net = owed - owe
  return (
    <div className="glass relative overflow-hidden rounded-[32px] p-5">
      <div aria-hidden className="pointer-events-none absolute -right-10 -top-12 size-48 rounded-full bg-[radial-gradient(circle,rgb(255_200_50/0.30),transparent_68%)]" />
      <div className="flex items-stretch gap-3">
        <div className="min-w-0 flex-1">
          <div className="text-lg font-bold">Overview</div>
          <dl className="mt-3 space-y-1.5 text-[13px]">
            <div className="flex gap-2"><dt className="text-muted">You are owed</dt><dd className="truncate font-bold text-pos">{formatPaise(owed)}</dd></div>
            <div className="flex gap-2"><dt className="text-muted">You owe</dt><dd className="truncate font-bold text-neg">{formatPaise(owe)}</dd></div>
          </dl>
          <PillButton onClick={onNewGroup} className="mt-3">New group</PillButton>
        </div>
        <Dashed className="relative flex w-[124px] shrink-0 flex-col items-center justify-center rounded-[24px] px-2.5 py-4 text-center">
          <div className="text-xs text-muted">Net balance</div>
          <div className="mt-1.5 max-w-full truncate text-xl font-extrabold">{formatPaise(Math.abs(net))}</div>
          <div className="mt-2.5 grid size-9 place-items-center rounded-full bg-accent/15 text-accent ring-1 ring-accent/25"><Icon name="wallet" size={19} /></div>
          <div className="mt-2 text-[11.5px] text-muted">{net === 0 ? 'all settled' : net > 0 ? 'you are owed' : 'you owe'}</div>
        </Dashed>
      </div>
    </div>
  )
}

function NewGroupSheet({ open, onClose, onCreate }: { open: boolean; onClose: () => void; onCreate: (name: string) => Promise<void> }) {
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    await onCreate(name.trim())
    setBusy(false)
    setName('')
  }
  return (
    <Sheet open={open} onClose={onClose} title="New group">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Group name">
          <input className={inputCls} autoFocus autoCapitalize="words" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <button type="submit" disabled={!name.trim() || busy}
          className="btn-accent h-13 w-full rounded-full py-3.5 font-bold disabled:opacity-45">Create</button>
      </form>
    </Sheet>
  )
}

