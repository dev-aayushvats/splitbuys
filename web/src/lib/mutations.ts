import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'
import { keys } from './queries'
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
    onSettled: () => Promise.all([qc.invalidateQueries({ queryKey: key }), qc.invalidateQueries({ queryKey: keys.groups(uid) })]),
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
      gid === undefined || opts?.forget ? undefined : qc.invalidateQueries({ queryKey: keys.group(uid, gid) }),
    ])
  }
}
