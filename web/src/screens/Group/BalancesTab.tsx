import { useState } from 'react'
import { useRecordPayment } from '../../lib/mutations'
import { useIdempotencyKey } from '../../lib/idempotency'
import { formatPaise, hundredthsToInput, parseHundredths } from '../../lib/money'
import { canPayViaUpi } from '../../lib/upi'
import { label, type GroupData, type Transfer, type User } from '../../lib/types'
import { Avatar, AvatarStack, Dashed, Field, Icon, PillButton, SectionTitle, Sheet, Spinner, Tile, tap, inputCls, useFeedback } from '../../ui'
import { who } from './shared'

/** `fresh`: the balances on screen have just been confirmed by the server, not merely remembered from an earlier visit. */
export function BalancesTab({ data, me, fresh }: { data: GroupData; me: User; fresh: boolean }) {
  const record = useRecordPayment(data.group.id)
  // remember which transfer is open, not a copy of it: the amount and link must follow the data as it is refreshed
  const [payingPair, setPayingPair] = useState<{ from: number; to: number } | null>(null)
  const payKey = useIdempotencyKey()
  const plan = data.balances.settleUp
  const paying = payingPair ? plan.find((t) => t.from.id === payingPair.from && t.to.id === payingPair.to) ?? null : null

  return (
    <div className="space-y-6">
      <section>
        <SectionTitle>Settle up</SectionTitle>
        <Dashed className="space-y-3 p-3">
          {plan.length === 0 ? <p className="p-3.5 text-center text-muted">Everyone is settled up.</p> : plan.map((t) => (
            <Tile key={`${t.from.id}-${t.to.id}`} leading={<AvatarStack users={[t.from, t.to]} size={34} max={2} />}
              title={`${who(t.from, me)} ${t.from.id === me.id ? 'pay' : 'pays'} ${t.to.id === me.id ? 'you' : label(t.to)}`}
              subtitle={formatPaise(t.amountPaise)}
              trailing={<PillButton className="h-9 px-3.5 text-[13px]" onClick={() => setPayingPair({ from: t.from.id, to: t.to.id })}>{canPayViaUpi(t) ? 'Pay' : 'Record'}</PillButton>} />
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

      <PaymentSheet transfer={paying} me={me} fresh={fresh} onClose={() => setPayingPair(null)} onSubmit={async (paise) => {
        const t = paying!
        // the sheet closes and the balances update at once; a failure rolls them back and shows the error
        record.mutate({ fromId: t.from.id, toId: t.to.id, amountPaise: paise, key: payKey.keyFor([t.from.id, t.to.id, paise]) }, { onSuccess: payKey.done })
        setPayingPair(null)
      }} />
    </div>
  )
}

function PaymentSheet({ transfer, me, fresh, onClose, onSubmit }: {
  transfer: Transfer | null; me: User; fresh: boolean; onClose: () => void; onSubmit: (paise: number) => Promise<void>
}) {
  return (
    <Sheet open={!!transfer} onClose={onClose} title={transfer ? `${who(transfer.from, me)} → ${who(transfer.to, me)}` : ''}>
      {transfer && (
        <div className="space-y-5">
          {canPayViaUpi(transfer) && <UpiPay transfer={transfer} fresh={fresh} />}
          {transfer.upiId && !canPayViaUpi(transfer) && (
            <p className="px-1 text-[12.5px] text-muted">UPI apps don't allow a single payment above ₹1,00,000. Pay in parts, then record each payment below.</p>
          )}
          <PaymentForm key={`${transfer.from.id}-${transfer.to.id}`} transfer={transfer} onSubmit={onSubmit} />
        </div>
      )}
    </Sheet>
  )
}

/**
 * The receiver's UPI ID and the amount, to copy into the user's own UPI app. There is deliberately no `upi://pay` link:
 * Kotak, Samsung Money and GPay refuse payments started from a link to a personal UPI ID. No UPI app can tell us whether
 * the payment happened, so the user records it below.
 */
function UpiPay({ transfer, fresh }: { transfer: Transfer; fresh: boolean }) {
  const { toast } = useFeedback()
  async function copy(text: string, done: string) {
    try { await navigator.clipboard.writeText(text); toast(done) } catch { toast('Could not copy') }
  }
  const row = 'flex items-center gap-2 rounded-[20px] bg-black/25 py-1.5 pl-3.5 pr-1.5 ring-1 ring-white/10'
  return (
    <div className="space-y-3">
      <div className={row}>
        <span className="min-w-0 flex-1 select-all truncate text-[13px] text-muted">{label(transfer.to)} · {transfer.upiId}</span>
        <PillButton onClick={() => copy(transfer.upiId!, 'UPI ID copied')} className="h-9 px-3.5 text-[13px]"><Icon name="copy" size={15} />Copy ID</PillButton>
      </div>
      <div className={row}>
        <span className="min-w-0 flex-1 truncate text-[15px] font-bold">{formatPaise(transfer.amountPaise)}</span>
        {/* the amount may have changed since this was last loaded: wait for the server before offering to copy it */}
        <PillButton onClick={() => copy(hundredthsToInput(transfer.amountPaise), 'Amount copied')} disabled={!fresh} className="h-9 px-3.5 text-[13px]">
          {fresh ? <Icon name="copy" size={15} /> : <Spinner className="size-4" />}Copy amount
        </PillButton>
      </div>
      <p className="px-1 text-[12.5px] text-muted">
        Pay this UPI ID in your UPI app, then record it below so everyone's balance updates. We can't tell whether you paid.
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
