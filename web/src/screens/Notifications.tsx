import { usePush, type PushState } from '../lib/push'
import { Spinner, useFeedback } from '../ui'

/** Only the states that need explaining get words; on and off speak for themselves. */
const NOTE: Partial<Record<PushState, string>> = {
  unsupported: "This browser can't show them",
  'needs-install': 'Add SplitBuys to your Home Screen first (Share, then Add to Home Screen)',
  blocked: 'Blocked: allow them in your browser or phone settings',
}

/** One row of the Profile card: a switch for notifications on this device. Hidden when the server has them off. */
export function NotificationsRow() {
  const { state, busy, enable, disable } = usePush()
  const { toast } = useFeedback()
  if (state === 'unavailable') return null // do not offer what cannot work
  const on = state === 'on'
  const canToggle = state === 'off' || on
  const fail = (e: unknown) => toast(e instanceof Error ? e.message : 'Something went wrong')
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="min-w-0 flex-1">
        <div className="font-medium">Notifications</div>
        {NOTE[state] && <div className="mt-0.5 text-[12.5px] text-muted">{NOTE[state]}</div>}
      </div>
      {state === 'checking' || busy ? <Spinner className="size-5" /> : (
        <button type="button" role="switch" aria-checked={on} aria-label="Notifications on this device" disabled={!canToggle}
          onClick={() => (on ? disable() : enable()).catch(fail)}
          className={`relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:opacity-40 ${on ? 'bg-accent' : 'bg-white/15'}`}>
          <span className={`absolute left-0.5 top-0.5 size-6 rounded-full bg-white shadow transition-transform ${on ? 'translate-x-5' : ''}`} />
        </button>
      )}
    </div>
  )
}
