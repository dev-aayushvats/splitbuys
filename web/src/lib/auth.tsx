import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, getSavedToken, setToken, setUnauthorizedHandler } from './api'
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

  // sign-out leaves nothing behind on this device: token, cached data and the persisted cache all go
  const signOut = useCallback(() => { setToken(null); wipeLocalData(); setUser(null) }, [])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    const saved = getSavedToken()
    if (!saved) { setReady(true); return }
    setToken(saved)
    api.me()
      .then(setUser)
      .catch((e) => {
        // an expired token is dropped; on other errors (e.g. offline) keep it saved for next launch
        if (e instanceof ApiError && e.status === 401) signOut()
      })
      .finally(() => setReady(true))
  }, [signOut])

  const value = useMemo<AuthValue>(() => ({
    user,
    ready,
    signOut,
    async verify(email, code) {
      const r = await api.verifyOtp(email, code)
      queryClient.clear() // nothing from a previous session may be shown to the person who just signed in
      setToken(r.token)
      setUser(r.user)
    },
    async setName(name) { setUser(await api.setName(name)) },
    async setUpi(upiId) { setUser(await api.setUpi(upiId)) },
    async clearUpi() { setUser(await api.clearUpi()) },
    // the server has erased the account, so the saved token is worthless: sign out only after it said yes
    async deleteAccount(email) { await api.deleteAccount(email); signOut() },
  }), [user, ready, signOut])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
