import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { keys } from '../lib/queries'
import { resync } from '../lib/push'
import { safeAppPath } from '../lib/pushPayload'

/**
 * Listens for the service worker while signed in:
 *  - a push arrived: refetch that group (and the list), so the screen is current before anyone taps the notification;
 *  - a notification was tapped: go to its screen without reloading the app.
 * It also re-registers this device with the server on launch (see resync).
 */
export function PushSync() {
  const uid = useAuth().user!.id
  const qc = useQueryClient()
  const nav = useNavigate()

  useEffect(() => { void resync() }, [])

  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const onMessage = (e: MessageEvent) => {
      const d = e.data as { type?: string; groupId?: unknown; url?: unknown } | null
      if (d?.type === 'push') {
        void qc.invalidateQueries({ queryKey: keys.groups(uid) })
        if (typeof d.groupId === 'number') void qc.invalidateQueries({ queryKey: keys.group(uid, d.groupId) })
      } else if (d?.type === 'open') {
        nav(safeAppPath(d.url))
      }
    }
    navigator.serviceWorker.addEventListener('message', onMessage)
    return () => navigator.serviceWorker.removeEventListener('message', onMessage)
  }, [uid, qc, nav])

  return null
}
