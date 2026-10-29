import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { AuthPage, ConfirmButton, inputCls, useFeedback } from '../ui'

export function LoginScreen() {
  const { verify } = useAuth()
  const { toast } = useFeedback()
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try { await action() } catch (e) { toast(e instanceof Error ? e.message : 'Something went wrong') } finally { setBusy(false) }
  }
  const sendCode = () => run(async () => { await api.requestOtp(email.trim()); setSent(true) })
  // On success the route gate redirects away.
  const doVerify = () => run(() => verify(email.trim(), code.trim()))
  const submit = (e: FormEvent) => { e.preventDefault(); sent ? doVerify() : sendCode() }

  return (
    <AuthPage
      title={sent ? 'Check your email' : 'Split bills with friends'}
      subtitle={sent ? `We sent a 6-digit code to ${email.trim()}` : 'Sign in with your email to get started'}
    >
      <form onSubmit={submit} className="space-y-5">
        {!sent ? (
          <>
            <input className={inputCls} type="email" inputMode="email" autoComplete="email" placeholder="Email"
              aria-label="Email" value={email} disabled={busy} onChange={(e) => setEmail(e.target.value)} />
            <ConfirmButton type="submit" busy={busy} disabled={!email.includes('@')}>Send code</ConfirmButton>
          </>
        ) : (
          <>
            <input className={`${inputCls} text-center text-[26px] font-bold tracking-[0.5em]`} inputMode="numeric"
              autoComplete="one-time-code" maxLength={6} placeholder="······" aria-label="Code" autoFocus
              value={code} disabled={busy} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
            <ConfirmButton type="submit" busy={busy} disabled={code.length < 6}>Verify</ConfirmButton>
            <div className="flex justify-center gap-2">
              <button type="button" disabled={busy} onClick={sendCode} className="px-3 py-2 font-semibold text-accent">Resend code</button>
              <button type="button" disabled={busy} onClick={() => { setSent(false); setCode('') }} className="px-3 py-2 font-semibold text-accent">Change email</button>
            </div>
          </>
        )}
      </form>
    </AuthPage>
  )
}
