import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, getSavedToken, setToken, setUnauthorizedHandler } from './api'
import type { User } from './types'

interface AuthValue {
  user: User | null
  ready: boolean
  verify: (email: string, code: string) => Promise<void>
  setName: (name: string) => Promise<void>
  signOut: () => void
}

const Ctx = createContext<AuthValue>(null!)
export const useAuth = () => useContext(Ctx)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)

  const signOut = useCallback(() => { setToken(null); setUser(null) }, [])

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
      setToken(r.token)
      setUser(r.user)
    },
    async setName(name) { setUser(await api.setName(name)) },
  }), [user, ready, signOut])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
