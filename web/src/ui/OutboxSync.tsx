import { useEffect } from 'react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useRefreshGroups } from '../lib/mutations'
import { outbox } from '../lib/outbox'
import { useQueuedExpenses } from '../lib/useOutbox'
import { formatPaise } from '../lib/money'
import { useFeedback } from './feedback'

const RETRY_EVERY_MS = 20_000

/** Sends the expenses added while offline: when the app opens, when the connection or the tab comes back, and every 20 s while any are waiting. Renders nothing. */
export function OutboxSync() {
  const uid = useAuth().user!.id
  const { toast } = useFeedback()
  const refresh = useRefreshGroups()
  const waiting = useQueuedExpenses().filter((q) => !q.error).length

  // biome-ignore lint/correctness/useExhaustiveDependencies: `refresh` and `toast` change identity on every render; the effect only needs the user and whether anything is waiting
  useEffect(() => {
    const box = outbox()
    const run = async () => {
      if (box.waiting(uid).length === 0) return
      const { sent, refused } = await box.flush(uid, (q) => api.addExpense(q.gid, q.body, q.key))
      for (const gid of new Set(sent.map((q) => q.gid))) void refresh(gid)
      if (sent.length === 1) toast(`Synced “${sent[0].body.description}” · ${formatPaise(sent[0].body.amountPaise)}`)
      else if (sent.length > 1) toast(`Synced ${sent.length} expenses`)
      if (refused.length > 0) toast(`“${refused[0].body.description}” could not be saved: ${refused[0].error}`)
    }
    void run()
    const onVisible = () => { if (document.visibilityState === 'visible') void run() }
    const onOnline = () => { void run() }
    window.addEventListener('online', onOnline)
    document.addEventListener('visibilitychange', onVisible)
    const timer = waiting > 0 ? window.setInterval(() => { void run() }, RETRY_EVERY_MS) : 0
    return () => {
      window.removeEventListener('online', onOnline)
      document.removeEventListener('visibilitychange', onVisible)
      window.clearInterval(timer)
    }
  }, [uid, waiting > 0])

  return null
}
