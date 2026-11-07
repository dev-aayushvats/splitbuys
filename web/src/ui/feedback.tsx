import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { Sheet } from './sheet'

interface Feedback {
  toast: (msg: string) => void
  confirm: (title: string, action?: string) => Promise<boolean>
}

const FeedbackCtx = createContext<Feedback>(null!)

export const useFeedback = () => useContext(FeedbackCtx)

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null)
  const timer = useRef<number>(0)
  const [ask, setAsk] = useState<{ title: string; action: string; done: (ok: boolean) => void } | null>(null)

  const toast = useCallback((m: string) => {
    setMsg(m)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMsg(null), 3200)
  }, [])
  const confirm = useCallback(
    (title: string, action = 'Delete') => new Promise<boolean>((done) => setAsk({ title, action, done })),
    [],
  )
  const close = (ok: boolean) => { ask?.done(ok); setAsk(null) }

  return (
    <FeedbackCtx.Provider value={{ toast, confirm }}>
      {children}
      {msg && (
        <div role="status" className="glass-strong animate-rise fixed bottom-28 left-1/2 z-[60] w-max max-w-[calc(100%-3rem)] -translate-x-1/2 rounded-full px-5 py-3 text-center text-sm font-medium">
          {msg}
        </div>
      )}
      <Sheet open={!!ask} onClose={() => close(false)} title={ask?.title}>
        <div className="flex gap-3">
          <button type="button" className="h-13 flex-1 rounded-full glass-btn py-3.5 font-semibold" onClick={() => close(false)}>Cancel</button>
          <button type="button" className="h-13 flex-1 rounded-full bg-neg py-3.5 font-bold text-on-accent shadow-[0_10px_28px_-8px_rgb(255_90_90/0.6),inset_0_1px_0_rgb(255_255_255/0.4)]" onClick={() => close(true)}>{ask?.action}</button>
        </div>
      </Sheet>
    </FeedbackCtx.Provider>
  )
}

/** Light haptic tick where the browser supports it (Android); a no-op elsewhere. */
export const tap = () => { try { navigator.vibrate?.(8) } catch { /* unsupported */ } }

export function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true), off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off) }
  }, [])
  if (online) return null
  return (
    <div role="alert" className="glass-strong animate-rise pointer-events-none fixed left-1/2 top-[calc(0.75rem+env(safe-area-inset-top))] z-[70] w-max max-w-[calc(100%-2rem)] -translate-x-1/2 rounded-full px-4 py-2 text-center text-[13px] font-semibold text-neg">
      You're offline · changes won't save
    </div>
  )
}
