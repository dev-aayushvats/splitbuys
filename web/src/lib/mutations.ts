import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'
import { keys } from './queries'
import { findUndo } from './history'
import { withGroupName, withoutExpense, withoutMember, withoutSettlement, withSettlement } from './optimistic'
import type { GroupData } from './types'
import { useFeedback } from '../ui'

/**
 * A write to one group that updates the screen first and the server second:
 *   onMutate  cancel in-flight reads, snapshot the group, apply the change to the cache
 *   onError   put the snapshot back and show the server's message
 *   onSettled refetch the group and the groups list, so what stays on screen is what the server says
 */
function useGroupWrite<V>(gid: number, write: (v: V) => Promise<unknown>, optimistic?: (d: GroupData, v: V) => GroupData) {
  const qc = useQueryClient()
  const { toast } = useFeedback()
  const uid = useAuth().user!.id
  const key = keys.group(uid, gid)
  return useMutation({
    mutationFn: write,
    onMutate: async (v: V) => {
      await qc.cancelQueries({ queryKey: key })
      const previous = qc.getQueryData<GroupData>(key)
      if (previous && optimistic) qc.setQueryData<GroupData>(key, optimistic(previous, v))
      return { previous }
    },
    onError: (e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(key, ctx.previous)
      toast(e instanceof Error ? e.message : 'Something went wrong')
    },
    // friends' balances are sums over groups, and the activity feed lists their rows, so both go stale whenever a group's money or membership changes
    onSettled: () => Promise.all([
      qc.invalidateQueries({ queryKey: key }),
      qc.invalidateQueries({ queryKey: keys.groups(uid) }),
      qc.invalidateQueries({ queryKey: keys.friends(uid) }),
      qc.invalidateQueries({ queryKey: keys.activity(uid) }),
      qc.invalidateQueries({ queryKey: keys.spending(uid) }),
    ]),
  })
}

export const useDeleteExpense = (gid: number) => useGroupWrite(gid, (id: number) => api.deleteExpense(gid, id), withoutExpense)
export const useDeleteSettlement = (gid: number) => useGroupWrite(gid, (id: number) => api.deleteSettlement(gid, id), withoutSettlement)
export const useRenameGroup = (gid: number) => useGroupWrite(gid, (name: string) => api.renameGroup(gid, name), withGroupName)
export const useRemoveMember = (gid: number) => useGroupWrite(gid, (userId: number) => api.removeMember(gid, userId), withoutMember)
export const useRecordPayment = (gid: number) => useGroupWrite(
  gid,
  (p: { fromId: number; toId: number; amountPaise: number; key: string }) => api.addSettlement(gid, p.fromId, p.toId, p.amountPaise, p.key),
  (d, p) => withSettlement(d, p.fromId, p.toId, p.amountPaise))
/** No optimistic step: the server decides whether the undo is still allowed, and a restored expense gets a new id. The history list is keyed under the group, so the group's own invalidation refreshes it too. */
export const useUndoChange = (gid: number) => useGroupWrite(gid, (historyId: number) => api.undoChange(gid, historyId))
/** No optimistic step: the new invite link is made by the server. */
export const useRegenerateInvite = (gid: number) => useGroupWrite(gid, () => api.regenerateInvite(gid))

/** After a write that is not tied to one open group screen (create, join, leave, delete, the expense form). */
export function useRefreshGroups() {
  const qc = useQueryClient()
  const uid = useAuth().user!.id
  return (gid?: number, opts?: { forget?: boolean }) => {
    // a group the user has left or deleted must not linger in the cache, and must not be refetched (it would 404)
    if (gid !== undefined && opts?.forget) qc.removeQueries({ queryKey: keys.group(uid, gid) })
    return Promise.all([
      qc.invalidateQueries({ queryKey: keys.groups(uid) }),
      qc.invalidateQueries({ queryKey: keys.friends(uid) }),
      qc.invalidateQueries({ queryKey: keys.activity(uid) }),
      qc.invalidateQueries({ queryKey: keys.spending(uid) }),
      gid === undefined || opts?.forget ? undefined : qc.invalidateQueries({ queryKey: keys.group(uid, gid) }),
    ])
  }
}

/**
 * "Undo" on the toast shown right after the user deletes or edits an expense. The history entry is looked up only when Undo is
 * tapped (nothing extra is fetched otherwise), and only the user's own latest change to that expense is undone: if someone else
 * changed it in between, or the server no longer allows it, the user is pointed to the history sheet instead.
 */
export function useOfferUndo() {
  const { toast } = useFeedback()
  const refresh = useRefreshGroups()
  const me = useAuth().user!
  return (gid: number, expenseId: number, action: 'EDITED' | 'DELETED') => {
    const done = action === 'DELETED' ? 'Expense restored' : 'Edit undone'
    toast(action === 'DELETED' ? 'Expense deleted' : 'Expense updated', {
      label: 'Undo',
      run: async () => {
        try {
          const entry = findUndo((await api.history(gid)).items, expenseId, action, me.id)
          if (!entry) { toast("Can't undo this any more. See Recent changes in the group."); return }
          await api.undoChange(gid, entry.id)
          toast(done)
        } catch (e) {
          toast(e instanceof Error ? e.message : 'Something went wrong')
        } finally {
          void refresh(gid)
        }
      },
    })
  }
}
