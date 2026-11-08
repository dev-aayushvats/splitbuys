import { useEffect, useState } from 'react'
import { peekCache, putCache } from '../lib/cache'

/**
 * Loads data for a screen. With `cacheKey` it is stale-while-revalidate: a revisit renders the last result immediately
 * (`loading` stays true while the fresh copy is fetched) and swaps the new data in when it arrives. Only pass a key for
 * read-only screens: a form that copies the data into its own state would not pick up the refreshed copy.
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[], cacheKey?: string) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>(
    () => ({ data: cacheKey ? peekCache<T>(cacheKey) : undefined, loading: true }))
  const [tick, setTick] = useState(0)
  // biome-ignore lint/correctness/useExhaustiveDependencies: the caller supplies the dependency list, and tick re-runs the load on reload()
  useEffect(() => {
    let live = true
    const cached = cacheKey ? peekCache<T>(cacheKey) : undefined
    setState((s) => ({ data: cached ?? s.data, error: undefined, loading: true }))
    load().then((data) => {
      if (cacheKey) putCache(cacheKey, data)
      if (live) setState({ data, loading: false })
    }, (error) => live && setState({ error, loading: false }))
    return () => { live = false }
  }, [...deps, tick])
  return { ...state, reload: () => setTick((t) => t + 1) }
}
