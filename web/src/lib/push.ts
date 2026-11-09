import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

// Notifications on this device: the browser's subscription (made with the server's public VAPID key) is registered with
// the API, which then sends a message to the browser vendor's push service whenever someone changes a group.

const ENDPOINT_KEY = 'push-endpoint' // which subscription this tab registered, so sign-out can remove it from the server
const READY_TIMEOUT_MS = 4000

export type PushState =
  | 'checking'
  | 'unavailable' // the server has no keys configured, or there is no service worker (e.g. the dev server)
  | 'unsupported' // this browser cannot do push at all
  | 'needs-install' // iPhone/iPad: only works from the Home Screen app
  | 'blocked' // the person said no, or turned it off in settings
  | 'off'
  | 'on'

/** iPadOS reports itself as a Mac, hence the touch check. */
const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
const isInstalled = () => (navigator as { standalone?: boolean }).standalone === true || matchMedia('(display-mode: standalone)').matches

export function support(): 'ok' | 'needs-install' | 'unsupported' {
  if ('serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window) return 'ok'
  return isIos() && !isInstalled() ? 'needs-install' : 'unsupported'
}

/** The service worker, or null where there isn't one (dev server) rather than waiting forever. */
async function registration(): Promise<ServiceWorkerRegistration | null> {
  return Promise.race([navigator.serviceWorker.ready, new Promise<null>((r) => setTimeout(() => r(null), READY_TIMEOUT_MS))])
}

/** The base64url public key as the bytes pushManager.subscribe wants. */
export function keyToBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const padded = base64url.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(base64url.length / 4) * 4, '=')
  const raw = atob(padded)
  const out = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
  return out
}

function toBody(sub: PushSubscription) {
  const json = sub.toJSON()
  const p256dh = json.keys?.p256dh
  const auth = json.keys?.auth
  if (!json.endpoint || !p256dh || !auth) throw new Error('This browser gave an incomplete subscription')
  return { endpoint: json.endpoint, keys: { p256dh, auth } }
}

async function currentState(): Promise<PushState> {
  const s = support()
  if (s !== 'ok') return s
  if (Notification.permission === 'denied') return 'blocked'
  const cfg = await api.pushConfig().catch(() => null)
  if (!cfg?.enabled) return 'unavailable'
  const reg = await registration()
  if (!reg) return 'unavailable'
  const sub = await reg.pushManager.getSubscription()
  return sub && Notification.permission === 'granted' ? 'on' : 'off'
}

async function enable() {
  if ((await Notification.requestPermission()) !== 'granted') throw new Error('Notifications were not allowed')
  const cfg = await api.pushConfig()
  if (!cfg.enabled || !cfg.publicKey) throw new Error('Notifications are not available right now')
  const reg = await registration()
  if (!reg) throw new Error('Notifications need the installed app or a secure connection')
  const key = keyToBytes(cfg.publicKey)
  let sub: PushSubscription
  try {
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
  } catch {
    // a subscription made for an earlier server key blocks a new one; drop it and try once more
    await (await reg.pushManager.getSubscription())?.unsubscribe()
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
  }
  try {
    await api.subscribePush(toBody(sub))
  } catch (e) {
    await sub.unsubscribe() // never leave the browser subscribed to something the server does not know about
    throw e
  }
  try { localStorage.setItem(ENDPOINT_KEY, sub.endpoint) } catch { /* private mode */ }
}

async function disable() {
  const reg = await registration()
  const sub = await reg?.pushManager.getSubscription()
  if (!sub) return
  await api.unsubscribePush(sub.endpoint).catch(() => {}) // the browser side is what matters; the server cleans up dead ones itself
  await sub.unsubscribe()
  try { localStorage.removeItem(ENDPOINT_KEY) } catch { /* private mode */ }
}

/**
 * Called on every launch of a signed-in app: if this browser is subscribed, tell the server again. It is a no-op when the
 * server already knows, and repairs the case where it lost the row (a restore, or the device being used by someone else).
 */
export async function resync() {
  try {
    if (support() !== 'ok' || Notification.permission !== 'granted') return
    const cfg = await api.pushConfig()
    if (!cfg.enabled) return
    const sub = await (await registration())?.pushManager.getSubscription()
    if (sub) await api.subscribePush(toBody(sub))
  } catch { /* best effort */ }
}

/**
 * Sign-out: this device must stop receiving the signed-out person's notifications. The server is told while the token still
 * works (the request is started before the token is cleared), and the browser's own subscription is dropped regardless,
 * which also covers a sign-out forced by an expired token when the server can no longer be asked.
 */
export function forgetThisDevice() {
  let endpoint: string | null = null
  try { endpoint = localStorage.getItem(ENDPOINT_KEY) } catch { /* private mode */ }
  if (endpoint) api.unsubscribePush(endpoint).catch(() => {})
  if (!('serviceWorker' in navigator)) return
  navigator.serviceWorker.getRegistration()
    .then((reg) => reg?.pushManager.getSubscription())
    .then((sub) => sub?.unsubscribe())
    .catch(() => {})
}

export function usePush() {
  const [state, setState] = useState<PushState>('checking')
  const [busy, setBusy] = useState(false)
  const refresh = useCallback(() => { currentState().then(setState, () => setState('unavailable')) }, [])
  useEffect(refresh, [refresh])

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try { await action() } finally { setBusy(false); refresh() }
  }
  return { state, busy, enable: () => run(enable), disable: () => run(disable) }
}
