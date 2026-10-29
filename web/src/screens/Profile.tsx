import { useState, type FormEvent } from 'react'
import { useAuth } from '../lib/auth'
import { label } from '../lib/types'
import { Avatar, Field, Icon, Screen, TopBar, inputCls, useFeedback } from '../ui'

export function ProfileScreen() {
  const { user, setName, signOut } = useAuth()
  const { toast, confirm } = useFeedback()
  const me = user!
  const [name, setNameText] = useState(me.name ?? '')
  const [busy, setBusy] = useState(false)
  const changed = name.trim() !== (me.name ?? '') && name.trim() !== ''

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!changed) return
    setBusy(true)
    try { await setName(name.trim()); toast('Name updated') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setBusy(false)
  }

  return (
    <Screen className="pb-safe">
      <TopBar title="Profile" />
      <div className="flex flex-col items-center px-5 pt-6">
        <Avatar user={me} size={88} />
        <div className="mt-4 text-2xl font-extrabold">{label(me)}</div>
        <div className="mt-1 text-sm text-muted">{me.email}</div>
      </div>

      <form onSubmit={save} className="mt-8 space-y-4 px-5">
        <Field label="Display name">
          <input className={inputCls} autoCapitalize="words" value={name} onChange={(e) => setNameText(e.target.value)} />
        </Field>
        <button type="submit" disabled={!changed || busy}
          className="h-13 w-full rounded-full bg-accent py-3.5 font-bold text-on-accent transition-colors disabled:bg-high disabled:text-muted">
          Save
        </button>
      </form>

      <div className="mt-auto px-5 pb-6 pt-10">
        <button type="button" onClick={async () => { if (await confirm('Sign out of SplitBuys?', 'Sign out')) signOut() }}
          className="flex w-full items-center justify-center gap-2 rounded-full border border-line py-3.5 font-semibold text-neg">
          <Icon name="logout" size={18} /> Sign out
        </button>
      </div>
    </Screen>
  )
}
