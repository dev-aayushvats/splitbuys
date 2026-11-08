import { useEffect, useState } from 'react'

export function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>({ loading: true })
  const [tick, setTick] = useState(0)
  // biome-ignore lint/correctness/useExhaustiveDependencies: the caller supplies the dependency list, and tick re-runs the load on reload()
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, error: undefined, loading: true }))
    load().then((data) => live && setState({ data, loading: false }), (error) => live && setState({ error, loading: false }))
    return () => { live = false }
  }, [...deps, tick])
  return { ...state, reload: () => setTick((t) => t + 1) }
}
