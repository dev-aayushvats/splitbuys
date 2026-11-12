import { QueryClient } from '@tanstack/react-query'
import { createSyncStoragePersister } from '@tanstack/query-sync-storage-persister'
import { outbox } from './outbox'
import { THEME_KEY } from './theme'

const DAY = 24 * 60 * 60 * 1000

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000, // a revisit inside 10 s trusts the cache; after that it shows it and refetches behind the scenes
      gcTime: DAY, // must be at least the persistence maxAge, or entries are dropped before they can be restored
      retry: false, // lib/http.ts already retries transient failures
      networkMode: 'always', // fail fast with the cached data still on screen, instead of pausing until "online"
    },
    mutations: { retry: false, networkMode: 'always' },
  },
})

export const persister = createSyncStoragePersister({ storage: typeof window === 'undefined' ? undefined : window.localStorage, key: 'splitbuys-cache' })

/** Only queries that opt in with `meta: { persist: true }` are written to disk, and only once they have succeeded. */
export const persistOptions = {
  persister,
  maxAge: DAY,
  buster: 'v1', // bump when the shape of cached data changes, so old entries are discarded instead of misread
  dehydrateOptions: { shouldDehydrateQuery: (q: { state: { status: string }; meta?: Record<string, unknown> }) => q.state.status === 'success' && q.meta?.persist === true },
}

/** Everything the app keeps on this device: cached data, the sign-in token, the persisted cache (but not the theme setting). Called on sign-out. */
export function wipeLocalData() {
  void queryClient.cancelQueries() // an in-flight response must not repopulate the cache we are about to empty
  queryClient.clear()
  outbox().clear()
  try {
    // the theme is a setting of this device, not data of the person who signed out
    const theme = localStorage.getItem(THEME_KEY)
    localStorage.clear(); sessionStorage.clear()
    if (theme) localStorage.setItem(THEME_KEY, theme)
  } catch { /* storage unavailable (private mode) */ }
}
