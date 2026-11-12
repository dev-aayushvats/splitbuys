import type { ExpenseCategory } from './types'

/** What the user typed and which category chip is on. Both optional; together they narrow a list of expenses. */
export interface SearchFilter {
  text: string
  category: ExpenseCategory | null
}

export const NO_FILTER: SearchFilter = { text: '', category: null }

/** The server stops at 100 characters; cutting here means a long paste still searches instead of failing. */
export const MAX_SEARCH = 100

export const normalizedText = (f: SearchFilter) => f.text.trim().slice(0, MAX_SEARCH)

export const isFiltering = (f: SearchFilter) => normalizedText(f) !== '' || f.category !== null

/** The query-string part for a list request: empty when not filtering, otherwise starting with `&`. */
export function filterQuery(f: SearchFilter): string {
  const text = normalizedText(f)
  return (text ? `&q=${encodeURIComponent(text)}` : '') + (f.category ? `&category=${f.category}` : '')
}
