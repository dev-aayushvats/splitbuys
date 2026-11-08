import { useEffect, useState } from 'react'

/** The logo pops in over the app's own background, then the overlay fades away: 0.3s in total, once per page load. */
export function Splash() {
  const [done, setDone] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setDone(true), 300)
    return () => clearTimeout(t)
  }, [])
  if (done) return null
  return (
    <div aria-hidden className="animate-splash pointer-events-none fixed inset-0 z-[100] grid place-items-center bg-outer">
      <img src="/pwa-192.png" alt="" width={96} height={96} className="animate-splash-logo size-24 rounded-[32px] shadow-[0_18px_44px_-14px_rgb(255_140_15/0.5)]" />
    </div>
  )
}
