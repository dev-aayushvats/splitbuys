import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'
import { NO_FILTER, normalizedText, type SearchFilter } from './searchFilter'

// Keys start with the user's id, so one person's cached data can never be shown to another, even if a sign-out were missed.
export const keys = {
  groups: (uid: number) => ['u', uid, 'groups'] as const,
  activity: (uid: number) => ['u', uid, 'activity'] as const,
  friends: (uid: number) => ['u', uid, 'friends'] as const,
  spending: (uid: number) => ['u', uid, 'spending'] as const,
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

/** The user's own spending by month. Any change to a group's money makes it stale, so writes invalidate it. */
export function useSpendingInsights() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.spending(uid), queryFn: () => api.spending(), meta: { persist: true } })
}

export function useFriends() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.friends(uid), queryFn: () => api.friends(), meta: { persist: true } })
}

/** The cross-group feed, a page at a time (older pages on request). Not kept on disk: it is long, and always fresher online. */
export function useActivity(filter: SearchFilter = NO_FILTER) {
  const uid = useAuth().user!.id
  return useInfiniteQuery({
    // the filter is part of the key (after the user's activity prefix): every search is its own cached list, and a write that invalidates the prefix refreshes them all
    queryKey: [...keys.activity(uid), normalizedText(filter), filter.category] as const,
    queryFn: ({ pageParam }) => api.activity(pageParam, filter),
    placeholderData: keepPreviousData,
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
}

/** The expenses of one group that match a search, a page at a time. Only runs while the user is actually searching. */
export function useExpenseSearch(gid: number, filter: SearchFilter, enabled: boolean) {
  const uid = useAuth().user!.id
  return useInfiniteQuery({
    queryKey: [...keys.group(uid, gid), 'search', normalizedText(filter), filter.category] as const,
    queryFn: ({ pageParam }) => api.expenses(gid, pageParam, filter),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    placeholderData: keepPreviousData,
    enabled,
  })
}

/** The notes on one expense. Keyed under the group, so anything that refreshes the group refreshes them. Only fetched while the expense is open. */
export function useComments(gid: number, expenseId: number | undefined) {
  const uid = useAuth().user!.id
  return useQuery({
    queryKey: [...keys.group(uid, gid), 'comments', expenseId] as const,
    queryFn: () => api.comments(gid, expenseId!),
    enabled: expenseId !== undefined,
  })
}
