import { useState, type FormEvent } from 'react'
import { useAuth } from '../lib/auth'
import { AuthPage, ConfirmButton, inputCls, useFeedback } from '../ui'

export function NameScreen() {
  const { setName } = useAuth()
  const { toast } = useFeedback()
  const [name, setNameText] = useState('')
  const [busy, setBusy] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try { await setName(name.trim()) } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong'); setBusy(false) }
  }

  return (
    <AuthPage title="What should we call you?" subtitle="Friends will see this name in your groups">
      <form onSubmit={save} className="space-y-5">
        <input className={inputCls} placeholder="Your name" aria-label="Your name" autoFocus autoCapitalize="words"
          autoComplete="name" value={name} onChange={(e) => setNameText(e.target.value)} />
        <ConfirmButton type="submit" busy={busy} disabled={!name.trim()}>Continue</ConfirmButton>
      </form>
    </AuthPage>
  )
}
