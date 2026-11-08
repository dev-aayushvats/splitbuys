import { useState, type FormEvent } from 'react'
import { useAuth } from '../lib/auth'
import { label } from '../lib/types'
import { isValidUpi, normalizeUpi } from '../lib/upi'
import { DeleteAccountSheet } from './DeleteAccount'
import { Avatar, Field, Icon, Screen, TopBar, inputCls, useFeedback } from '../ui'

export function ProfileScreen() {
  const { user, setName, setUpi, clearUpi, signOut } = useAuth()
  const { toast, confirm } = useFeedback()
  const me = user!
  const [deleting, setDeleting] = useState(false)
  const [name, setNameText] = useState(me.name ?? '')
  const [busy, setBusy] = useState(false)
  const changed = name.trim() !== (me.name ?? '') && name.trim() !== ''
  const [upi, setUpiText] = useState(me.upiId ?? '')
  const [upiBusy, setUpiBusy] = useState(false)
  const upiChanged = normalizeUpi(upi) !== (me.upiId ?? '')
  const upiInvalid = upi.trim() !== '' && !isValidUpi(upi)

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!changed) return
    setBusy(true)
    try { await setName(name.trim()); toast('Name updated') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setBusy(false)
  }

  async function saveUpi(e: FormEvent) {
    e.preventDefault()
    if (!upiChanged || upiInvalid || upi.trim() === '') return
    setUpiBusy(true)
    try { await setUpi(normalizeUpi(upi)); setUpiText(normalizeUpi(upi)); toast('UPI ID saved') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setUpiBusy(false)
  }

  async function removeUpi() {
    if (!(await confirm('Remove your UPI ID?\nPeople will no longer get a pay link for you.', 'Remove'))) return
    setUpiBusy(true)
    try { await clearUpi(); setUpiText(''); toast('UPI ID removed') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setUpiBusy(false)
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
          className="btn-accent h-13 w-full rounded-full py-3.5 font-bold disabled:bg-none disabled:bg-white/10 disabled:text-muted disabled:shadow-none">
          Save
        </button>
      </form>

      <form onSubmit={saveUpi} className="mt-8 space-y-3 px-5">
        <Field label="UPI ID for receiving money">
          <input className={inputCls} inputMode="email" autoCapitalize="none" autoCorrect="off" spellCheck={false}
            placeholder="name@bank" value={upi} onChange={(e) => setUpiText(e.target.value)} />
        </Field>
        {upiInvalid
          ? <p className="px-1 text-sm text-neg">Enter a valid UPI ID, like name@bank</p>
          : <p className="px-1 text-[12.5px] text-muted">Shown only to people who owe you, as a pay link when they settle up.</p>}
        <div className="flex gap-3">
          {me.upiId && (
            <button type="button" onClick={removeUpi} disabled={upiBusy} className="glass-lite rounded-full px-5 py-3.5 font-semibold text-neg">Remove</button>
          )}
          <button type="submit" disabled={!upiChanged || upiInvalid || upi.trim() === '' || upiBusy}
            className="btn-accent h-13 flex-1 rounded-full py-3.5 font-bold disabled:bg-none disabled:bg-white/10 disabled:text-muted disabled:shadow-none">
            {me.upiId ? 'Update UPI ID' : 'Save UPI ID'}
          </button>
        </div>
      </form>

      <div className="mt-auto px-5 pb-6 pt-10">
        <button type="button" onClick={async () => { if (await confirm('Sign out of SplitBuys?', 'Sign out')) signOut() }}
          className="glass-lite flex w-full items-center justify-center gap-2 rounded-full py-3.5 font-semibold text-neg">
          <Icon name="logout" size={18} /> Sign out
        </button>
        <button type="button" onClick={() => setDeleting(true)} className="mt-3 w-full py-2.5 text-[13.5px] font-semibold text-muted underline-offset-2 hover:underline">
          Delete account
        </button>
      </div>
      <DeleteAccountSheet open={deleting} onClose={() => setDeleting(false)} email={me.email} />
    </Screen>
  )
}
