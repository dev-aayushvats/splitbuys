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

/** The value, but only after it has stopped changing for `ms`: a search box should not send a request per keystroke. */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
