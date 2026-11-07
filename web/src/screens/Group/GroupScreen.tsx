import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { formatPaise } from '../../lib/money'
import { Dock, ErrorRetry, Field, Icon, InfoCard, RoundButton, Screen, Sheet, Skeleton, TopBar, tap, inputCls, useAsync, useFeedback } from '../../ui'
import { ExpensesTab } from './ExpensesTab'
import { BalancesTab } from './BalancesTab'
import { MembersTab } from './MembersTab'

export function GroupScreen() {
  const id = Number(useParams().id)
  const nav = useNavigate()
  const { user } = useAuth()
  const { toast, confirm } = useFeedback()
  const { data, error, loading, reload } = useAsync(() => api.groupData(id), [id])
  const [tab, setTab] = useState(0)
  const direction = useRef(1) // which way the page slides in: forward when the new tab is to the right
  const swipe = useRef<{ x: number; y: number } | null>(null)
  const goTo = (i: number) => { if (i === tab) return; direction.current = i > tab ? 1 : -1; setTab(i) }
  const [menu, setMenu] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const me = user!

  const attempt = async (fn: () => Promise<void>) => {
    try { await fn() } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') }
  }

  if (!data) {
    return (
      <Screen>
        <TopBar title="" />
        {error ? <ErrorRetry error={error} onRetry={reload} /> : loading && (
          <div className="space-y-4 px-5 pt-1">
            <div className="flex gap-3"><Skeleton className="h-32 flex-1 rounded-3xl" /><Skeleton className="h-32 flex-1 rounded-3xl" /></div>
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-[78px] rounded-[22px]" />)}
          </div>
        )}
      </Screen>
    )
  }

  const total = data.balances.totalSpentPaise // from the server: the list below is only the newest page
  const mine = data.balances.balances.find((b) => b.user.id === me.id)?.netPaise ?? 0

  return (
    <Screen onRefresh={reload}>
      <TopBar title={data.group.name} action={<RoundButton label="Group menu" onClick={() => setMenu(true)}><Icon name="more" size={22} /></RoundButton>} />
      <div className="mt-4 flex gap-3 px-5">
        <InfoCard icon="receipt" label="Total spent" value={formatPaise(total)} />
        <InfoCard icon="wallet" label={mine === 0 ? 'Your balance' : mine > 0 ? 'You are owed' : 'You owe'}
          value={mine === 0 ? 'Settled up' : formatPaise(Math.abs(mine))}
          valueClass={mine === 0 ? '' : mine > 0 ? 'text-pos' : 'text-neg'} />
      </div>
      <main key={tab} className={`${direction.current > 0 ? 'animate-slide-fwd' : 'animate-slide-back'} flex-1 px-5 pb-40 pt-5`}
        onTouchStart={(e) => { swipe.current = { x: e.touches[0].clientX, y: e.touches[0].clientY } }}
        onTouchEnd={(e) => {
          const s0 = swipe.current
          swipe.current = null
          if (!s0) return
          const dx = e.changedTouches[0].clientX - s0.x
          const dy = e.changedTouches[0].clientY - s0.y
          // a deliberate sideways swipe moves between Expenses / Balances / Members
          if (Math.abs(dx) > 64 && Math.abs(dx) > Math.abs(dy) * 1.6) goTo(Math.min(2, Math.max(0, tab + (dx < 0 ? 1 : -1))))
        }}>
        {tab === 0 && <ExpensesTab data={data} me={me} onChanged={reload} />}
        {tab === 1 && <BalancesTab data={data} me={me} onChanged={reload} />}
        {tab === 2 && <MembersTab data={data} me={me} onChanged={reload} />}
      </main>

      <Dock index={tab} onChange={goTo}
        items={[{ label: 'Expenses', icon: 'receipt' }, { label: 'Balances', icon: 'wallet' }, { label: 'Members', icon: 'groups' }]} />

      {/* the add button floats above the dock, on the right */}
      {tab === 0 && (
        <div className="pointer-events-none fixed inset-x-0 bottom-[calc(6.25rem+env(safe-area-inset-bottom))] z-30 mx-auto flex max-w-[480px] justify-end px-5">
          <button type="button" aria-label="Add expense" onClick={() => { tap(); nav(`/groups/${id}/expense/new`) }}
            className="btn-accent animate-pop pointer-events-auto grid size-16 place-items-center rounded-full">
            <Icon name="plus" size={32} />
          </button>
        </div>
      )}

      <Sheet open={menu} onClose={() => setMenu(false)}>
        <div className="space-y-3">
          <button type="button" className="w-full rounded-full glass-btn py-3.5 font-semibold" onClick={() => { setMenu(false); setRenaming(true) }}>Rename group</button>
          <button type="button" className="w-full rounded-full glass-btn py-3.5 font-semibold" onClick={async () => {
            setMenu(false)
            if (!(await confirm('Reset invite link?\nThe current link will stop working.', 'Reset'))) return
            await attempt(async () => { await api.regenerateInvite(id); reload() })
          }}>Reset invite link</button>
          <button type="button" className="w-full rounded-full glass-btn py-3.5 font-semibold text-neg" onClick={async () => {
            setMenu(false)
            if (!(await confirm('Leave this group?', 'Leave'))) return
            await attempt(async () => { await api.leaveGroup(id); nav('/', { replace: true }) })
          }}>Leave group</button>
          {data.group.members.length === 1 && (
            <button type="button" className="w-full rounded-full border border-neg/40 bg-neg/10 py-3.5 font-semibold text-neg" onClick={async () => {
              setMenu(false)
              if (!(await confirm('Delete this group?\nAll its expenses and payments will be erased.', 'Delete'))) return
              await attempt(async () => { await api.deleteGroup(id); nav('/', { replace: true }) })
            }}>Delete group</button>
          )}
        </div>
      </Sheet>

      <RenameSheet open={renaming} current={data.group.name} onClose={() => setRenaming(false)} onSave={(name) => attempt(async () => {
        await api.renameGroup(id, name)
        setRenaming(false)
        reload()
      })} />
    </Screen>
  )
}

function RenameSheet({ open, current, onClose, onSave }: { open: boolean; current: string; onClose: () => void; onSave: (name: string) => Promise<void> }) {
  const [name, setName] = useState(current)
  const [busy, setBusy] = useState(false)
  const changed = name.trim() !== '' && name.trim() !== current
  return (
    <Sheet open={open} onClose={onClose} title="Rename group">
      <form className="space-y-4" onSubmit={async (e) => {
        e.preventDefault()
        if (!changed) return
        setBusy(true)
        await onSave(name.trim())
        setBusy(false)
      }}>
        <Field label="Group name">
          <input className={inputCls} autoFocus autoCapitalize="words" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <button type="submit" disabled={!changed || busy} className="btn-accent h-13 w-full rounded-full py-3.5 font-bold disabled:bg-none disabled:bg-white/10 disabled:text-muted disabled:shadow-none">Save</button>
      </form>
    </Sheet>
  )
}
