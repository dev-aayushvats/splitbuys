import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatPaise, hundredthsToInput, parseHundredths } from '../lib/money'
import { label, type Expense, type GroupDetail, type SplitInput, type SplitType, type User } from '../lib/types'
import {
  Avatar, ConfirmButton, Dashed, ErrorRetry, Field, Icon, PillTabs, RoundButton, Screen, SectionTitle, Spinner, TopBar,
  inputCls, tap, useAsync, useFeedback,
} from '../ui'

const KINDS: { api: SplitType; label: string }[] = [
  { api: 'EQUAL', label: 'Equal' },
  { api: 'PERCENT', label: 'Percent' },
  { api: 'EXACT', label: 'Exact' },
  { api: 'SHARES', label: 'Shares' },
]

export function ExpenseFormScreen() {
  const params = useParams()
  const gid = Number(params.id)
  const expenseId = params.expenseId ? Number(params.expenseId) : null
  const { data, error, loading, reload } = useAsync(async () => {
    const [group, expenses] = await Promise.all([api.group(gid), expenseId ? api.expenses(gid) : Promise.resolve([] as Expense[])])
    return { group, expense: expenseId ? expenses.find((e) => e.id === expenseId) ?? null : null }
  }, [gid, expenseId])

  if (!data) {
    return (
      <Screen>
        <TopBar title={expenseId ? 'Edit expense' : 'Add expense'} />
        {error ? <ErrorRetry error={error} onRetry={reload} /> : loading && <div className="grid flex-1 place-items-center"><Spinner /></div>}
      </Screen>
    )
  }
  if (expenseId && !data.expense) {
    return <Screen><TopBar title="Edit expense" /><ErrorRetry error={new Error('Expense not found')} onRetry={reload} /></Screen>
  }
  return <Form group={data.group} expense={data.expense} />
}

function Form({ group, expense }: { group: GroupDetail; expense: Expense | null }) {
  const nav = useNavigate()
  const { user } = useAuth()
  const { toast, confirm } = useFeedback()
  const me = user!
  const members = group.members

  const [description, setDescription] = useState(expense?.description ?? '')
  const [amount, setAmount] = useState(expense ? hundredthsToInput(expense.amountPaise) : '')
  const [payerId, setPayerId] = useState(expense?.payer.id ?? me.id)
  const [selected, setSelected] = useState<Set<number>>(
    () => new Set(expense ? expense.shares.map((s) => s.user.id).filter((id) => members.some((m) => m.id === id)) : members.map((m) => m.id)),
  )
  // Only resulting amounts are stored, so percentage/share splits reopen as exact amounts.
  const [kind, setKind] = useState<SplitType>(expense ? (expense.splitType === 'EQUAL' ? 'EQUAL' : 'EXACT') : 'EQUAL')
  const [values, setValues] = useState<Record<number, string>>(() =>
    expense && expense.splitType !== 'EQUAL' ? Object.fromEntries(expense.shares.map((s) => [s.user.id, hundredthsToInput(s.amountPaise)])) : {},
  )
  const [note, setNote] = useState(
    expense && expense.splitType !== 'EQUAL' && expense.splitType !== 'EXACT'
      ? `This was split by ${expense.splitType === 'PERCENT' ? 'percentage' : 'shares'}; shown here as exact amounts.` : null,
  )
  const [busy, setBusy] = useState(false)

  const nameOf = (id: number) => label(members.find((m) => m.id === id)!)

  const result = useMemo((): { splits?: SplitInput[]; problem?: string; hint?: string } => {
    const total = parseHundredths(amount)
    if (!description.trim()) return { problem: 'Add a description' }
    if (total === null || total <= 0) return { problem: 'Enter a valid amount' }
    if (selected.size === 0) return { problem: 'Pick at least one person' }
    const ids = members.map((m) => m.id).filter((id) => selected.has(id))

    if (kind === 'EQUAL') return { splits: ids.map((userId) => ({ userId })), hint: `${formatPaise(Math.floor(total / ids.length))} each` }

    if (kind === 'SHARES') {
      const weights: Record<number, number> = {}
      for (const id of ids) {
        const t = (values[id] ?? '').trim()
        const w = t === '' ? 1 : /^\d+$/.test(t) ? parseInt(t, 10) : NaN
        if (Number.isNaN(w)) return { problem: 'Shares must be whole numbers' }
        weights[id] = w
      }
      if (Object.values(weights).every((w) => w === 0)) return { problem: 'At least one share must be above zero' }
      return { splits: ids.map((userId) => ({ userId, value: weights[userId] })), hint: 'Empty fields count as 1 share' }
    }

    const isPercent = kind === 'PERCENT'
    const amounts: Record<number, number> = {}
    for (const id of ids) {
      const t = (values[id] ?? '').trim()
      const v = t === '' ? 0 : parseHundredths(t)
      if (v === null) return { problem: `Check the number for ${nameOf(id)}` }
      amounts[id] = v
    }
    const sum = Object.values(amounts).reduce((a, b) => a + b, 0)
    const diff = (isPercent ? 10000 : total) - sum
    if (diff !== 0) {
      const left = isPercent ? `${hundredthsToInput(Math.abs(diff))}%` : formatPaise(Math.abs(diff))
      return { problem: diff > 0 ? `${left} left to assign` : `Over by ${left}` }
    }
    return {
      splits: ids.map((userId) => ({ userId, value: isPercent ? amounts[userId] / 100 : amounts[userId] })),
      hint: isPercent ? 'Adds up to 100%' : `Adds up to ${formatPaise(total)}`,
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amount, description, selected, kind, values, members])

  function pickKind(k: SplitType) {
    if (k === kind) return
    setKind(k)
    setNote(null)
    setValues({})
  }
  const toggle = (id: number) => { tap(); setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n }) }

  async function save() {
    if (!result.splits) return
    setBusy(true)
    const body = { description: description.trim(), amountPaise: parseHundredths(amount)!, payerId, splitType: kind, splits: result.splits }
    try {
      if (expense) await api.updateExpense(group.id, expense.id, body)
      else await api.addExpense(group.id, body)
      nav(-1)
    } catch (e) {
      setBusy(false)
      toast(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  async function remove() {
    if (!expense || !(await confirm('Delete this expense?'))) return
    setBusy(true)
    try { await api.deleteExpense(group.id, expense.id); nav(-1) } catch (e) {
      setBusy(false)
      toast(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  const suffix = kind === 'PERCENT' ? '%' : kind === 'EXACT' ? '₹' : null

  return (
    <Screen>
      <TopBar title={expense ? 'Edit expense' : 'Add expense'}
        action={expense && <RoundButton label="Delete" disabled={busy} onClick={remove}><Icon name="trash" /></RoundButton>} />
      <form className="flex flex-1 flex-col" onSubmit={(e) => { e.preventDefault(); save() }}>
        <div className="flex-1 space-y-4 px-5 pb-6 pt-2">
          <Dashed className="px-4 py-[18px] text-center">
            <div className="text-[13px] text-muted">Total bill</div>
            <div className="mt-1 flex items-center justify-center gap-2">
              <span className="text-4xl font-extrabold text-muted">₹</span>
              <input aria-label="Amount" inputMode="decimal" placeholder="0.00" autoFocus={!expense} value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="amount-input bg-transparent text-center text-4xl font-extrabold outline-none placeholder:text-line" />
            </div>
          </Dashed>

          <Field label="What was it for?">
            <input className={inputCls} autoCapitalize="sentences" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>

          <Field label="Paid by">
            <select className={`${inputCls} appearance-none`} value={payerId} onChange={(e) => setPayerId(Number(e.target.value))}>
              {members.map((m) => <option key={m.id} value={m.id}>{m.id === me.id ? `${label(m)} (you)` : label(m)}</option>)}
            </select>
          </Field>

          <div className="pt-2">
            <SectionTitle>Split</SectionTitle>
            <PillTabs labels={KINDS.map((k) => k.label)} index={KINDS.findIndex((k) => k.api === kind)} onChange={(i) => pickKind(KINDS[i].api)} />
            {note && <p className="mt-2 text-[12.5px] text-muted">{note}</p>}
          </div>

          <div className="space-y-2.5">
            {members.map((m) => (
              <PersonRow key={m.id} user={m} name={m.id === me.id ? `${label(m)} (you)` : label(m)} selected={selected.has(m.id)}
                onToggle={() => toggle(m.id)}
                field={kind !== 'EQUAL' && selected.has(m.id) ? (
                  <div className="relative w-28" onClick={(e) => e.stopPropagation()}>
                    <input aria-label={`${label(m)} ${kind.toLowerCase()}`} inputMode={kind === 'SHARES' ? 'numeric' : 'decimal'}
                      placeholder={kind === 'SHARES' ? '1' : '0'} value={values[m.id] ?? ''}
                      onChange={(e) => setValues((v) => ({ ...v, [m.id]: e.target.value }))}
                      className={`w-full rounded-xl border border-line bg-bg py-2.5 pl-3 text-right outline-none focus:border-accent ${suffix ? 'pr-7' : 'pr-3'}`} />
                    {suffix && <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted">{suffix}</span>}
                  </div>
                ) : null} />
            ))}
          </div>

          <p role="status" className={`text-center font-semibold ${result.problem ? 'text-neg' : 'text-muted'}`}>{result.problem ?? result.hint ?? ''}</p>
        </div>

        <div className="pb-safe sticky bottom-0 bg-gradient-to-t from-bg from-70% to-transparent px-5 pb-4 pt-3">
          <ConfirmButton type="submit" busy={busy} disabled={!result.splits}>{expense ? 'Save changes' : 'Add expense'}</ConfirmButton>
        </div>
      </form>
    </Screen>
  )
}

function PersonRow({ user, name, selected, onToggle, field }: {
  user: User; name: string; selected: boolean; onToggle: () => void; field: React.ReactNode
}) {
  return (
    <div role="checkbox" aria-checked={selected} tabIndex={0} onClick={onToggle}
      onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && (e.preventDefault(), onToggle())}
      className={`flex items-center gap-3 rounded-[22px] border bg-surface py-2.5 pl-3 pr-3.5 ${selected ? 'border-accent/50' : 'border-line/60'}`}>
      <div className={selected ? '' : 'opacity-40'}><Avatar user={user} size={40} /></div>
      <span className={`min-w-0 flex-1 truncate font-semibold ${selected ? '' : 'text-muted'}`}>{name}</span>
      {field}
      <span className={`grid size-6 place-items-center rounded-full border-2 ${selected ? 'border-accent bg-accent text-on-accent' : 'border-muted'}`}>
        {selected && <Icon name="check" size={14} />}
      </span>
    </div>
  )
}
