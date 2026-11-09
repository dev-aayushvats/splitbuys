/// <reference lib="webworker" />
// The app's service worker (vite-plugin-pwa "injectManifest"): it does what the generated one did, which is to cache the app
// shell so it opens offline, and adds notifications: show a push message, and open the right screen when it is tapped.
import { clientsClaim } from 'workbox-core'
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { parsePayload } from './lib/pushPayload'

declare const self: ServiceWorkerGlobalScope

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)
// any page navigation gets the app shell (single-page app), except API calls
registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: [/^\/api/] }))
// an updated worker takes over at once, as registerType "autoUpdate" did
self.skipWaiting()
clientsClaim()

self.addEventListener('push', (event) => {
  event.waitUntil((async () => {
    let raw: unknown
    try { raw = event.data?.json() } catch { raw = undefined } // not JSON: show the fallback text
    const p = parsePayload(raw)
    // any open page refreshes its data straight away, so it is already up to date when the person looks at it
    for (const client of await self.clients.matchAll({ type: 'window', includeUncontrolled: true })) {
      client.postMessage({ type: 'push', groupId: p.groupId })
    }
    // every push must show a notification (browsers penalise silent ones), even if the app is open
    await self.registration.showNotification(p.title, {
      body: p.body, icon: '/pwa-192.png', badge: '/pwa-192.png', tag: p.tag, data: { url: p.url },
      // a newer message in the same group replaces the older one, and still alerts
      ...({ renotify: !!p.tag } as object),
    })
  })())
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const { url } = parsePayload({ url: event.notification.data?.url })
  event.waitUntil((async () => {
    const open = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const client = open[0]
    if (client) {
      await client.focus()
      client.postMessage({ type: 'open', url }) // the app navigates without reloading
    } else {
      await self.clients.openWindow(url)
    }
  })())
})
