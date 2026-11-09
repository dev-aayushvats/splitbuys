import { usePush, type PushState } from '../lib/push'
import { Icon, Spinner, useFeedback } from '../ui'

const EXPLAIN: Partial<Record<PushState, string>> = {
  unsupported: "This browser can't show notifications.",
  'needs-install': 'On iPhone and iPad, add SplitBuys to your Home Screen first (Share, then Add to Home Screen), then open it from there.',
  blocked: 'Notifications are blocked for SplitBuys. Allow them in your browser or phone settings, then come back.',
}

/** Profile section: get told when someone adds an expense or records a payment in your groups. */
export function NotificationsCard() {
  const { state, busy, enable, disable } = usePush()
  const { toast } = useFeedback()
  if (state === 'unavailable') return null // the server has them switched off: do not offer what cannot work
  const fail = (e: unknown) => toast(e instanceof Error ? e.message : 'Something went wrong')
  const on = state === 'on'
  const canToggle = state === 'off' || on

  return (
    <section className="mt-8 space-y-3 px-5">
      <div className="text-[13px] font-semibold text-muted">Notifications</div>
      <div className="glass-lite space-y-3 rounded-[24px] p-4">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-full bg-accent/15 text-accent ring-1 ring-accent/25"><Icon name="receipt" size={18} /></span>
          <div className="min-w-0 flex-1">
            <div className="font-semibold">{on ? 'On for this device' : 'Know when something changes'}</div>
            <p className="mt-0.5 text-[12.5px] text-muted">
              {EXPLAIN[state] ?? 'Get a notification when someone adds an expense or records a payment in your groups. You choose per device.'}
            </p>
          </div>
        </div>
        {state === 'checking' && <div className="flex justify-center py-1"><Spinner className="size-5" /></div>}
        {canToggle && (
          <button type="button" disabled={busy} onClick={() => (on ? disable() : enable()).catch(fail)}
            className={on
              ? 'glass-btn h-12 w-full rounded-full font-semibold disabled:opacity-60'
              : 'btn-accent h-12 w-full rounded-full font-bold disabled:bg-none disabled:bg-white/10 disabled:text-muted disabled:shadow-none'}>
            {busy ? 'One moment…' : on ? 'Turn off on this device' : 'Turn on notifications'}
          </button>
        )}
      </div>
    </section>
  )
}
