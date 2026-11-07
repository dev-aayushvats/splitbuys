import { useState } from 'react'
import { api } from '../../lib/api'
import { formatPaise, hundredthsToInput, parseHundredths } from '../../lib/money'
import { label, type GroupData, type Transfer, type User } from '../../lib/types'
import { Avatar, AvatarStack, Dashed, Field, Icon, PillButton, SectionTitle, Sheet, Tile, tap, inputCls, useFeedback } from '../../ui'
import { who } from './shared'

export function BalancesTab({ data, me, onChanged }: { data: GroupData; me: User; onChanged: () => void }) {
  const { toast } = useFeedback()
  const [paying, setPaying] = useState<Transfer | null>(null)
  const plan = data.balances.settleUp

  return (
    <div className="space-y-6">
      <section>
        <SectionTitle>Settle up</SectionTitle>
        <Dashed className="space-y-3 p-3">
          {plan.length === 0 ? <p className="p-3.5 text-center text-muted">Everyone is settled up.</p> : plan.map((t) => (
            <Tile key={`${t.from.id}-${t.to.id}`} leading={<AvatarStack users={[t.from, t.to]} size={34} max={2} />}
              title={`${who(t.from, me)} ${t.from.id === me.id ? 'pay' : 'pays'} ${t.to.id === me.id ? 'you' : label(t.to)}`}
              subtitle={formatPaise(t.amountPaise)}
              trailing={<PillButton className="h-9 px-3.5 text-[13px]" onClick={() => setPaying(t)}>{t.upiLink ? 'Pay' : 'Record'}</PillButton>} />
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
      {transfer && (
        <div className="space-y-5">
          {transfer.upiLink && <UpiPay transfer={transfer} />}
          <PaymentForm key={`${transfer.from.id}-${transfer.to.id}`} transfer={transfer} onSubmit={onSubmit} />
        </div>
      )}
    </Sheet>
  )
}

/** Opens the user's UPI app with the receiver and amount filled in. It cannot tell whether the payment happened. */
function UpiPay({ transfer }: { transfer: Transfer }) {
  const { toast } = useFeedback()
  async function copy() {
    try { await navigator.clipboard.writeText(transfer.upiId!); toast('UPI ID copied') } catch { toast('Could not copy') }
  }
  return (
    <div className="space-y-3">
      <a href={transfer.upiLink!} onClick={tap}
        className="btn-accent flex h-13 w-full items-center justify-center gap-2 rounded-full py-3.5 font-bold">
        <Icon name="payments" size={20} /> Pay {formatPaise(transfer.amountPaise)} via UPI
      </a>
      <div className="flex items-center gap-2 rounded-[20px] bg-black/25 py-1.5 pl-3.5 pr-1.5 ring-1 ring-white/10">
        <span className="min-w-0 flex-1 select-all truncate text-[13px] text-muted">{label(transfer.to)} · {transfer.upiId}</span>
        <PillButton onClick={copy} className="h-9 px-3.5 text-[13px]"><Icon name="copy" size={15} />Copy</PillButton>
      </div>
      <p className="px-1 text-[12.5px] text-muted">
        UPI apps can't tell us whether you paid. After paying, record it below so everyone's balance updates.
      </p>
    </div>
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
