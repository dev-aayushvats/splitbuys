import { dayLabel } from '../../lib/money'
import { historyView } from '../../lib/history'
import { useUndoChange } from '../../lib/mutations'
import { useHistory } from '../../lib/queries'
import type { User } from '../../lib/types'
import { EmptyState, ErrorRetry, IconBadge, Sheet, Skeleton, useFeedback } from '../../ui'

/** Everything that was added, edited or deleted in the group, newest first, with an Undo where the server still allows one. */
export function HistorySheet({ gid, me, open, onClose }: { gid: number; me: User; open: boolean; onClose: () => void }) {
  const { toast } = useFeedback()
  const { data, error, isPending, refetch, hasNextPage, fetchNextPage, isFetchingNextPage } = useHistory(gid, open)
  const undo = useUndoChange(gid)
  const items = data?.pages.flatMap((p) => p.items)
  const icon = (a: string) => (a === 'DELETED' ? 'trash' : a === 'CREATED' ? 'plus' : 'receipt') as 'trash' | 'plus' | 'receipt'
  return (
    <Sheet open={open} onClose={onClose} title="Recent changes">
      <div className="max-h-[60dvh] space-y-2 overflow-y-auto">
        {isPending && !items ? [0, 1, 2].map((i) => <Skeleton key={i} className="h-[64px] rounded-2xl" />)
          : error && !items ? <ErrorRetry error={error} onRetry={() => { void refetch() }} />
          : items && items.length === 0 ? <EmptyState icon="receipt" title="No changes yet">Edits and deletions will show up here.</EmptyState>
          : items?.map((h) => {
            const v = historyView(h, me)
            return (
              <div key={h.id} className="flex items-center gap-3 rounded-2xl bg-white/[0.07] px-3 py-2.5 ring-1 ring-white/10">
                <IconBadge icon={icon(h.action)} />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium">{v.title}</div>
                  <div className="truncate text-[12px] text-muted">{dayLabel(h.at)}{v.detail && ` · ${v.detail}`}</div>
                </div>
                {h.undoable && (
                  <button type="button" disabled={undo.isPending}
                    onClick={() => undo.mutate(h.id, { onSuccess: () => toast(h.action === 'DELETED' ? 'Expense restored' : 'Edit undone') })}
                    className="glass-btn shrink-0 rounded-full px-3.5 py-2 text-[13px] font-semibold disabled:opacity-60">Undo</button>
                )}
              </div>
            )
          })}
        {hasNextPage && (
          <button type="button" onClick={() => { void fetchNextPage() }} disabled={isFetchingNextPage}
            className="glass-lite w-full rounded-full py-3 font-semibold disabled:opacity-60">{isFetchingNextPage ? 'Loading…' : 'Load older'}</button>
        )}
      </div>
    </Sheet>
  )
}
