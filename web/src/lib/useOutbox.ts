import { useMemo, useSyncExternalStore } from 'react'
import { useAuth } from './auth'
import { outbox, type Queued } from './outbox'

/** The signed-in user's expenses that are waiting to be sent, optionally for one group. Re-renders when the queue changes. */
export function useQueuedExpenses(gid?: number): Queued[] {
  const box = outbox()
  const all = useSyncExternalStore(box.subscribe, box.getSnapshot)
  const uid = useAuth().user?.id
  return useMemo(() => all.filter((q) => q.uid === uid && (gid === undefined || q.gid === gid)), [all, uid, gid])
}
