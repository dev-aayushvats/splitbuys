// Expenses and payments are paged separately by the server but shown as one newest-first timeline.
// Showing everything loaded would be wrong: if expenses still has older pages, a payment older than the oldest loaded
// expense would appear while expenses that belong before it are missing. So the timeline is only drawn down to the
// "horizon": the newest of the oldest-loaded items among the lists that still have more to load.

export interface Timed { createdAt: string }
export type TimelineItem<E extends Timed, S extends Timed> =
  | { kind: 'expense'; at: string; item: E }
  | { kind: 'settlement'; at: string; item: S }

const ms = (iso: string) => Date.parse(iso) // not a string compare: fractional digits vary in length

export function mergeTimeline<E extends Timed, S extends Timed>(
  expenses: E[], moreExpenses: boolean, settlements: S[], moreSettlements: boolean,
): TimelineItem<E, S>[] {
  const all: TimelineItem<E, S>[] = [
    ...expenses.map((item) => ({ kind: 'expense' as const, at: item.createdAt, item })),
    ...settlements.map((item) => ({ kind: 'settlement' as const, at: item.createdAt, item })),
  ].sort((a, b) => ms(b.at) - ms(a.at))

  const cutoffs: number[] = []
  if (moreExpenses && expenses.length > 0) cutoffs.push(ms(expenses[expenses.length - 1].createdAt))
  if (moreSettlements && settlements.length > 0) cutoffs.push(ms(settlements[settlements.length - 1].createdAt))
  if (cutoffs.length === 0) return all
  const horizon = Math.max(...cutoffs)
  return all.filter((i) => ms(i.at) >= horizon)
}
