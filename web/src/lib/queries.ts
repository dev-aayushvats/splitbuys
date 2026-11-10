import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'

// Keys start with the user's id, so one person's cached data can never be shown to another, even if a sign-out were missed.
export const keys = {
  groups: (uid: number) => ['u', uid, 'groups'] as const,
  activity: (uid: number) => ['u', uid, 'activity'] as const,
  friends: (uid: number) => ['u', uid, 'friends'] as const,
  group: (uid: number, gid: number) => ['u', uid, 'group', gid] as const,
}

export function useGroups() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.groups(uid), queryFn: () => api.groups(), meta: { persist: true } })
}

export function useGroupData(gid: number) {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.group(uid, gid), queryFn: () => api.groupData(gid), meta: { persist: true } })
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
