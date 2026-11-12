import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../../lib/api'
import { useDeleteExpense, useDeleteSettlement } from '../../lib/mutations'
import { dayLabel, formatDate, formatPaise } from '../../lib/money'
import { mergeTimeline } from '../../lib/timeline'
import { label, type Expense, type GroupData, type Settlement, type User } from '../../lib/types'
import { Avatar, EmptyState, IconBadge, Sheet, Tile, useFeedback } from '../../ui'
import { HistorySheet } from './HistorySheet'
import { who } from './shared'

export function ExpensesTab({ data, me }: { data: GroupData; me: User }) {
  const nav = useNavigate()
  const { toast, confirm } = useFeedback()
  const gid = data.group.id
  const deleteExpense = useDeleteExpense(gid)
  const deleteSettlement = useDeleteSettlement(gid)
  const [detail, setDetail] = useState<Expense | null>(null)
  const [showHistory, setShowHistory] = useState(false)

  // First page comes with the group data; "Load more" appends older pages. A reload (after any change) starts over.
  const [older, setOlder] = useState<{ expenses: Expense[]; settlements: Settlement[]; eCursor: string | null; sCursor: string | null } | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  // biome-ignore lint/correctness/useExhaustiveDependencies: start over from the first page whenever the group data is reloaded
  useEffect(() => setOlder(null), [data])
  const expenses = [...data.expenses, ...(older?.expenses ?? [])]
  const settlements = [...data.settlements, ...(older?.settlements ?? [])]
  const eCursor = older ? older.eCursor : data.expensesCursor
  const sCursor = older ? older.sCursor : data.settlementsCursor

  async function loadMore() {
    setLoadingMore(true)
    try {
      const [e, s] = await Promise.all([
        eCursor ? api.expenses(gid, eCursor) : null,
        sCursor ? api.settlements(gid, sCursor) : null,
      ])
      setOlder({
        expenses: [...(older?.expenses ?? []), ...(e?.items ?? [])],
        settlements: [...(older?.settlements ?? []), ...(s?.items ?? [])],
        eCursor: e ? e.nextCursor : eCursor,
        sCursor: s ? s.nextCursor : sCursor,
      })
    } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setLoadingMore(false)
  }

  const items = mergeTimeline(expenses, eCursor !== null, settlements, sCursor !== null)
    .map((i) => ({ at: i.at, node: i.kind === 'expense' ? expenseTile(i.item) : settlementTile(i.item) }))
  const hasMore = eCursor !== null || sCursor !== null

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
          deleteSettlement.mutate(s.id)
        }} />
    )
  }

  if (items.length === 0 && !hasMore) {
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
          setDetail(null)
          deleteExpense.mutate(e.id)
        }} />
      <HistorySheet gid={gid} me={me} open={showHistory} onClose={() => setShowHistory(false)} />
      {days.map((d) => (
        <section key={d.day}>
          <h3 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-muted">{d.day}</h3>
          <div className="stagger space-y-3">{d.nodes}</div>
        </section>
      ))}
      {hasMore && (
        <button type="button" onClick={loadMore} disabled={loadingMore}
          className="glass-lite w-full rounded-full py-3.5 font-semibold disabled:opacity-60">
          {loadingMore ? 'Loading…' : 'Load older'}
        </button>
      )}
      <button type="button" onClick={() => setShowHistory(true)} className="w-full py-2 text-[13px] font-semibold text-muted">
        Recent changes and undo
      </button>
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
