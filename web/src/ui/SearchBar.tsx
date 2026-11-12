import { useRef, useState } from 'react'
import { CATEGORIES } from '../lib/categories'
import { isFiltering, MAX_SEARCH, NO_FILTER, type SearchFilter } from '../lib/searchFilter'
import { Icon } from './icons'
import { tap } from './feedback'

/** A search box, with category chips that appear once it is used. The caller owns the value and decides when to ask the server. */
export function SearchBar({ value, onChange, placeholder }: { value: SearchFilter; onChange: (f: SearchFilter) => void; placeholder: string }) {
  const [focused, setFocused] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const active = isFiltering(value)
  const showChips = focused || active
  return (
    <div className="space-y-2.5">
      <div className="glass-btn flex items-center gap-2.5 rounded-full px-4 shadow-[inset_0_2px_8px_rgb(0_0_0/0.28)] focus-within:ring-4 focus-within:ring-accent/15">
        <span className="text-muted"><Icon name="search" size={18} /></span>
        <input ref={input} type="search" enterKeyHint="search" aria-label={placeholder} placeholder={placeholder} maxLength={MAX_SEARCH}
          value={value.text} onChange={(e) => onChange({ ...value, text: e.target.value })}
          onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
          className="min-w-0 flex-1 bg-transparent py-3 text-[15px] outline-none placeholder:text-muted/70 [&::-webkit-search-cancel-button]:hidden" />
        {active && (
          <button type="button" aria-label="Clear search" onMouseDown={(e) => e.preventDefault()}
            onClick={() => { tap(); onChange(NO_FILTER); input.current?.blur() }}
            className="-mr-2 grid size-8 shrink-0 place-items-center rounded-full text-muted"><Icon name="close" size={16} /></button>
        )}
      </div>
      {showChips && (
        <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1" onMouseDown={(e) => e.preventDefault()}>
          {CATEGORIES.map((c) => (
            <button key={c.id} type="button" aria-pressed={value.category === c.id}
              onClick={() => { tap(); onChange({ ...value, category: value.category === c.id ? null : c.id }) }}
              className={`shrink-0 rounded-full px-3.5 py-2 text-[13px] font-semibold ring-1 ring-white/10 ${value.category === c.id ? 'btn-accent' : 'glass-btn'}`}>
              {c.emoji} {c.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
