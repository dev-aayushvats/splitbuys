import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'

// Keys start with the user's id, so one person's cached data can never be shown to another, even if a sign-out were missed.
export const keys = {
  groups: (uid: number) => ['u', uid, 'groups'] as const,
  activity: (uid: number) => ['u', uid, 'activity'] as const,
  friends: (uid: number) => ['u', uid, 'friends'] as const,
  group: (uid: number, gid: number) => ['u', uid, 'group', gid] as const,
  history: (uid: number, gid: number) => ['u', uid, 'group', gid, 'history'] as const,
  summary: (uid: number, gid: number) => ['u', uid, 'group', gid, 'summary'] as const,
}

export function useGroups() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.groups(uid), queryFn: () => api.groups(), meta: { persist: true } })
}

export function useGroupData(gid: number) {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.group(uid, gid), queryFn: () => api.groupData(gid), meta: { persist: true } })
}

/** Where a group's money went, by category. Keyed under the group, so any write to the group refreshes it. */
export function useSpending(gid: number) {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.summary(uid, gid), queryFn: () => api.summary(gid) })
}

/** A group's change history (edits, deletions, restores), a page at a time. Not kept on disk. */
export function useHistory(gid: number, enabled: boolean) {
  const uid = useAuth().user!.id
  return useInfiniteQuery({
    queryKey: keys.history(uid, gid),
    queryFn: ({ pageParam }) => api.history(gid, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled,
  })
}

export function useFriends() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.friends(uid), queryFn: () => api.friends(), meta: { persist: true } })
}

/** The cross-group feed, a page at a time (older pages on request). Not kept on disk: it is long, and always fresher online. */
export function useActivity() {
  const uid = useAuth().user!.id
  return useInfiniteQuery({
    queryKey: keys.activity(uid),
    queryFn: ({ pageParam }) => api.activity(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
}
