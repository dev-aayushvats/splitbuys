import { useState, type FormEvent } from 'react'
import { useAuth } from '../lib/auth'
import { emailMatches } from '../lib/account'
import { Field, Sheet, Spinner, inputCls, useFeedback } from '../ui'

/** Account deletion: says what will happen, asks for the email to be typed, and shows the server's refusal in place. */
export function DeleteAccountSheet({ open, onClose, email }: { open: boolean; onClose: () => void; email: string }) {
  const { deleteAccount } = useAuth()
  const { toast } = useFeedback()
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  function close() {
    if (busy) return
    setTyped('')
    setProblem(null)
    onClose()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!emailMatches(typed, email) || busy) return
    setBusy(true)
    setProblem(null)
    try {
      await deleteAccount(typed.trim())
      toast('Your account was deleted')
    } catch (err) {
      setBusy(false)
      // e.g. "Settle up before deleting your account. Unsettled in: Goa trip"
      setProblem(err instanceof Error ? err.message : 'Something went wrong')
    }
  }

  return (
    <Sheet open={open} onClose={close} title="Delete your account?">
      <form onSubmit={submit} className="space-y-4">
        <ul className="list-disc space-y-1.5 pl-5 text-[14px] text-muted">
          <li>Your name, email and UPI ID are erased and you are signed out everywhere.</li>
          <li>You leave all your groups. Past expenses stay for the other members, shown as "Deleted user".</li>
          <li>You must be settled up in every group first.</li>
          <li>This cannot be undone. You can sign up again later as a new account.</li>
        </ul>
        <Field label={`Type ${email} to confirm`}>
          <input className={inputCls} inputMode="email" autoCapitalize="none" autoCorrect="off" spellCheck={false}
            value={typed} onChange={(e) => { setTyped(e.target.value); setProblem(null) }} />
        </Field>
        {problem && <p role="alert" className="px-1 text-sm text-neg">{problem}</p>}
        <div className="flex gap-3">
          <button type="button" onClick={close} disabled={busy} className="glass-btn flex-1 rounded-full py-3.5 font-semibold">Cancel</button>
          <button type="submit" disabled={!emailMatches(typed, email) || busy}
            className="flex flex-1 items-center justify-center gap-2 rounded-full bg-neg py-3.5 font-bold text-white disabled:opacity-40">
            {busy && <Spinner className="size-4" />} Delete account
          </button>
        </div>
      </form>
    </Sheet>
  )
}
