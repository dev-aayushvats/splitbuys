import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { dayLabel, formatDate, formatPaise, hundredthsToInput, parseHundredths } from '../lib/money'
import { label, type Expense, type GroupData, type Transfer, type User } from '../lib/types'
import {
  Avatar, AvatarStack, BackFab, Dashed, Dock, EmptyState, ErrorRetry, Field, Icon, IconBadge, InfoCard, PillButton, RoundButton, Screen,
  SectionTitle, Sheet, Skeleton, Tile, TopBar, tap, inputCls, useAsync, useFeedback,
} from '../ui'

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

  const total = data.expenses.reduce((s, e) => s + e.amountPaise, 0)
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

      {/* back and add float just above the dock, where a thumb rests */}
      <div className="pointer-events-none fixed inset-x-0 bottom-[calc(6.25rem+env(safe-area-inset-bottom))] z-30 mx-auto flex max-w-[480px] items-center justify-between px-5">
        <BackFab />
        {tab === 0 && (
          <button type="button" aria-label="Add expense" onClick={() => { tap(); nav(`/groups/${id}/expense/new`) }}
            className="btn-accent animate-pop pointer-events-auto grid size-16 place-items-center rounded-full">
            <Icon name="plus" size={32} />
          </button>
        )}
      </div>

      <Sheet open={menu} onClose={() => setMenu(false)}>
        <div className="space-y-3">
          <button className="w-full rounded-full glass-btn py-3.5 font-semibold" onClick={() => { setMenu(false); setRenaming(true) }}>Rename group</button>
          <button className="w-full rounded-full glass-btn py-3.5 font-semibold" onClick={async () => {
            setMenu(false)
            if (!(await confirm('Reset invite link?\nThe current link will stop working.', 'Reset'))) return
            await attempt(async () => { await api.regenerateInvite(id); reload() })
          }}>Reset invite link</button>
          <button className="w-full rounded-full glass-btn py-3.5 font-semibold text-neg" onClick={async () => {
            setMenu(false)
            if (!(await confirm('Leave this group?', 'Leave'))) return
            await attempt(async () => { await api.leaveGroup(id); nav('/', { replace: true }) })
          }}>Leave group</button>
          {data.group.members.length === 1 && (
            <button className="w-full rounded-full border border-neg/40 bg-neg/10 py-3.5 font-semibold text-neg" onClick={async () => {
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

const who = (u: User, me: User) => (u.id === me.id ? 'You' : label(u))

/* ---------- expenses ---------- */
function ExpensesTab({ data, me, onChanged }: { data: GroupData; me: User; onChanged: () => void }) {
  const nav = useNavigate()
  const { toast, confirm } = useFeedback()
  const gid = data.group.id
  const [detail, setDetail] = useState<Expense | null>(null)

  const items = [
    ...data.expenses.map((e) => ({ at: e.createdAt, node: expenseTile(e) })),
    ...data.settlements.map((s) => ({ at: s.createdAt, node: settlementTile(s) })),
  ].sort((a, b) => b.at.localeCompare(a.at))

  function expenseTile(e: GroupData['expenses'][number]) {
    const myShare = e.shares.filter((s) => s.user.id === me.id).reduce((a, s) => a + s.amountPaise, 0)
    let note: string, cls: string
    if (e.payer.id === me.id) {
      const lent = e.amountPaise - myShare
      note = lent > 0 ? `you lent\n${formatPaise(lent)}` : 'you paid'
      cls = lent > 0 ? 'text-pos' : 'text-muted'
    } else if (myShare > 0) {
      note = `you owe\n${formatPaise(myShare)}`
      cls = 'text-neg'
    } else {
      note = 'not involved'
      cls = 'text-muted'
    }
    return (
      <Tile key={`e${e.id}`} leading={<IconBadge icon="receipt" />} title={e.description}
        subtitle={`${who(e.payer, me)} paid ${formatPaise(e.amountPaise)}`}
        trailing={<div className={`whitespace-pre-line text-right text-[13px] font-bold ${cls}`}>{note}</div>}
        onClick={() => setDetail(e)} />
    )
  }

  function settlementTile(s: GroupData['settlements'][number]) {
    return (
      <Tile key={`s${s.id}`} leading={<IconBadge icon="payments" tone="pos" />}
        title={`${who(s.fromUser, me)} paid ${s.toUser.id === me.id ? 'you' : label(s.toUser)}`}
        subtitle="Payment · tap to delete"
        trailing={<span className="font-extrabold text-pos">{formatPaise(s.amountPaise)}</span>}
        onClick={async () => {
          if (!(await confirm(`Delete this payment of ${formatPaise(s.amountPaise)}?`))) return
          try { await api.deleteSettlement(gid, s.id); onChanged() } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') }
        }} />
    )
  }

  if (items.length === 0) {
    return <EmptyState icon="receipt" title="No expenses yet">Tap the + button to add the first one.</EmptyState>
  }
  const days: { day: string; nodes: React.ReactNode[] }[] = []
  for (const i of items) {
    const day = dayLabel(i.at)
    if (days.at(-1)?.day === day) days.at(-1)!.nodes.push(i.node)
    else days.push({ day, nodes: [i.node] })
  }
  return (
    <div className="space-y-5">
      <ExpenseDetail expense={detail} me={me}
        onClose={() => setDetail(null)}
        onEdit={(e) => nav(`/groups/${gid}/expense/${e.id}`)}
        onDelete={async (e) => {
          if (!(await confirm('Delete this expense?'))) return
          try { await api.deleteExpense(gid, e.id); setDetail(null); onChanged() } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
        }} />
      {days.map((d) => (
        <section key={d.day}>
          <h3 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-muted">{d.day}</h3>
          <div className="stagger space-y-3">{d.nodes}</div>
        </section>
      ))}
    </div>
  )
}

const SPLIT_LABEL = { EQUAL: 'Split equally', EXACT: 'Split by exact amounts', PERCENT: 'Split by percentage', SHARES: 'Split by shares' } as const

function ExpenseDetail({ expense: e, me, onClose, onEdit, onDelete }: {
  expense: Expense | null; me: User; onClose: () => void; onEdit: (e: Expense) => void; onDelete: (e: Expense) => void
}) {
  return (
    <Sheet open={!!e} onClose={onClose} title={e?.description}>
      {e && (
        <div className="space-y-4">
          <div>
            <div className="text-3xl font-extrabold">{formatPaise(e.amountPaise)}</div>
            <div className="mt-1 text-[13px] text-muted">
              {who(e.payer, me)} paid · {formatDate(e.createdAt)}
              {e.createdBy.id !== e.payer.id && ` · added by ${who(e.createdBy, me)}`}
            </div>
          </div>
          <div>
            <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">{SPLIT_LABEL[e.splitType]}</div>
            <div className="max-h-[40dvh] space-y-2 overflow-y-auto">
              {e.shares.map((sh) => (
                <div key={sh.user.id} className="flex items-center gap-3 rounded-2xl bg-white/[0.07] px-3 py-2.5 ring-1 ring-white/10">
                  <Avatar user={sh.user} size={32} />
                  <span className="min-w-0 flex-1 truncate font-medium">{sh.user.id === me.id ? `${label(sh.user)} (you)` : label(sh.user)}</span>
                  <span className="font-bold">{formatPaise(sh.amountPaise)}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="flex gap-3">
            <button type="button" onClick={() => onDelete(e)} className="flex-1 rounded-full glass-btn py-3.5 font-semibold text-neg">Delete</button>
            <button type="button" onClick={() => onEdit(e)} className="btn-accent flex-1 rounded-full py-3.5 font-bold">Edit</button>
          </div>
        </div>
      )}
    </Sheet>
  )
}

/* ---------- balances ---------- */
function BalancesTab({ data, me, onChanged }: { data: GroupData; me: User; onChanged: () => void }) {
  const { toast } = useFeedback()
  const [paying, setPaying] = useState<Transfer | null>(null)
  const plan = data.balances.settleUp

  return (
    <div className="space-y-6">
      <section>
        <SectionTitle>Settle up</SectionTitle>
        <Dashed className="space-y-3 p-3">
          {plan.length === 0 ? <p className="p-3.5 text-center text-muted">Everyone is settled up.</p> : plan.map((t, i) => (
            <Tile key={i} leading={<AvatarStack users={[t.from, t.to]} size={34} max={2} />}
              title={`${who(t.from, me)} ${t.from.id === me.id ? 'pay' : 'pays'} ${t.to.id === me.id ? 'you' : label(t.to)}`}
              subtitle={formatPaise(t.amountPaise)}
              trailing={<PillButton className="h-9 px-3.5 text-[13px]" onClick={() => setPaying(t)}>Record</PillButton>} />
          ))}
        </Dashed>
      </section>

      <section>
        <SectionTitle>Balances</SectionTitle>
        <div className="stagger space-y-3">
          {data.balances.balances.map((b) => (
            <Tile key={b.user.id} leading={<Avatar user={b.user} size={46} />}
              title={b.user.id === me.id ? `${label(b.user)} (you)` : label(b.user)}
              trailing={b.netPaise === 0 ? <span className="text-[12.5px] text-muted">settled up</span> : (
                <div className="text-right">
                  <div className="text-[11.5px] text-muted">{b.netPaise > 0 ? 'is owed' : 'owes'}</div>
                  <div className={`font-extrabold ${b.netPaise > 0 ? 'text-pos' : 'text-neg'}`}>{formatPaise(Math.abs(b.netPaise))}</div>
                </div>
              )} />
          ))}
        </div>
      </section>

      <PaymentSheet transfer={paying} me={me} onClose={() => setPaying(null)} onSubmit={async (paise) => {
        const t = paying!
        try {
          await api.addSettlement(data.group.id, t.from.id, t.to.id, paise)
          setPaying(null)
          onChanged()
        } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') }
      }} />
    </div>
  )
}

function PaymentSheet({ transfer, me, onClose, onSubmit }: {
  transfer: Transfer | null; me: User; onClose: () => void; onSubmit: (paise: number) => Promise<void>
}) {
  return (
    <Sheet open={!!transfer} onClose={onClose} title={transfer ? `${who(transfer.from, me)} → ${who(transfer.to, me)}` : ''}>
      {transfer && <PaymentForm key={`${transfer.from.id}-${transfer.to.id}`} transfer={transfer} onSubmit={onSubmit} />}
    </Sheet>
  )
}

function PaymentForm({ transfer, onSubmit }: { transfer: Transfer; onSubmit: (paise: number) => Promise<void> }) {
  const [text, setText] = useState(hundredthsToInput(transfer.amountPaise))
  const [busy, setBusy] = useState(false)
  const paise = parseHundredths(text)
  const valid = paise !== null && paise > 0
  return (
    <form className="space-y-4" onSubmit={async (e) => {
      e.preventDefault()
      if (!valid) return
      setBusy(true)
      await onSubmit(paise)
      setBusy(false)
    }}>
      <Field label="Amount paid">
        <div className="relative">
          <span className="absolute left-4 top-1/2 -translate-y-1/2 text-muted">₹</span>
          <input className={`${inputCls} pl-9`} inputMode="decimal" autoFocus value={text} onChange={(e) => setText(e.target.value)} />
        </div>
      </Field>
      {!valid && text !== '' && <p className="px-1 text-sm text-neg">Enter a valid amount</p>}
      <button type="submit" disabled={!valid || busy} className="btn-accent h-13 w-full rounded-full py-3.5 font-bold disabled:opacity-45">Record</button>
    </form>
  )
}

/* ---------- members ---------- */
function MembersTab({ data, me, onChanged }: { data: GroupData; me: User; onChanged: () => void }) {
  const { toast, confirm } = useFeedback()
  const { group } = data
  const link = `${window.location.origin}/join/${group.inviteToken}`

  async function share() {
    try {
      if (navigator.share) { await navigator.share({ title: group.name, text: `Join ${group.name} on SplitBuys`, url: link }); return }
      await navigator.clipboard.writeText(link)
      toast('Invite link copied')
    } catch (e) {
      if ((e as Error)?.name !== 'AbortError') toast('Could not share the link')
    }
  }

  return (
    <div className="space-y-6">
      <Dashed className="p-4 text-center">
        <div className="font-semibold">Splitting with</div>
        <div className="mt-2.5 flex justify-center"><AvatarStack users={group.members} size={44} max={5} /></div>
        <div className="mt-2 text-[12.5px] text-muted">{group.members.length} {group.members.length === 1 ? 'person' : 'people'}</div>
        <div className="mt-4 flex items-center gap-2 rounded-[20px] bg-black/25 py-1.5 pl-3.5 pr-1.5 ring-1 ring-white/10">
          <span className="min-w-0 flex-1 select-all truncate text-left text-[12.5px] text-muted">{link}</span>
          <PillButton onClick={share} className="h-10 px-3.5"><Icon name="copy" size={16} />{'share' in navigator ? 'Share' : 'Copy'}</PillButton>
        </div>
      </Dashed>

      <section>
        <SectionTitle>Members</SectionTitle>
        <div className="stagger space-y-3">
          {group.members.map((m) => (
            <Tile key={m.id} leading={<Avatar user={m} size={46} />} title={m.id === me.id ? `${label(m)} (you)` : label(m)}
              subtitle={m.name ? m.email : undefined}
              onClick={m.id === me.id ? undefined : async () => {
                if (!(await confirm(`Remove ${label(m)} from this group?\nThey must be settled up first.`, 'Remove'))) return
                try { await api.removeMember(group.id, m.id); toast(`${label(m)} removed`); onChanged() } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') }
              }} />
          ))}
        </div>
      </section>
    </div>
  )
}
