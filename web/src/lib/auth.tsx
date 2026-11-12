import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, getSavedToken, setToken, setUnauthorizedHandler } from './api'
import { forgetThisDevice } from './push'
import { saveProfile, savedProfile } from './profileCache'
import { queryClient, wipeLocalData } from './queryClient'
import type { Profile } from './types'

interface AuthValue {
  user: Profile | null
  ready: boolean
  verify: (email: string, code: string) => Promise<void>
  setName: (name: string) => Promise<void>
  setUpi: (upiId: string) => Promise<void>
  clearUpi: () => Promise<void>
  deleteAccount: (email: string) => Promise<void>
  signOut: () => void
}

const Ctx = createContext<AuthValue>(null!)
export const useAuth = () => useContext(Ctx)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null)
  const [ready, setReady] = useState(false)

  // sign-out leaves nothing behind on this device: notifications, token, cached data and the persisted cache all go
  const signOut = useCallback(() => { forgetThisDevice(); setToken(null); wipeLocalData(); setUser(null) }, [])

  // Every change to the profile also updates the copy kept on the device.
  const remember = useCallback((p: Profile) => { saveProfile(p); setUser(p) }, [])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    const saved = getSavedToken()
    if (!saved) { setReady(true); return }
    setToken(saved)
    // Open at once with the profile kept on the device (the token proves who it is; the server decides below whether that still
    // holds). Without this, a phone with no connection showed the sign-in screen, and only a later reload with a connection
    // brought the person back, which looked like being signed out and in again without logging in.
    const kept = savedProfile()
    if (kept) { setUser(kept); setReady(true) }
    api.me()
      .then(remember)
      .catch((e) => {
        // only the server saying "this token is no good" signs the person out; no connection, a timeout or a server error
        // leave them where they were
        if (e instanceof ApiError && e.status === 401) signOut()
      })
      .finally(() => setReady(true))
  }, [signOut, remember])

  const value = useMemo<AuthValue>(() => ({
    user,
    ready,
    signOut,
    async verify(email, code) {
      const r = await api.verifyOtp(email, code)
      queryClient.clear() // nothing from a previous session may be shown to the person who just signed in
      setToken(r.token)
      remember(r.user)
    },
    async setName(name) { remember(await api.setName(name)) },
    async setUpi(upiId) { remember(await api.setUpi(upiId)) },
    async clearUpi() { remember(await api.clearUpi()) },
    // the server has erased the account, so the saved token is worthless: sign out only after it said yes
    async deleteAccount(email) { await api.deleteAccount(email); signOut() },
  }), [user, ready, signOut, remember])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
