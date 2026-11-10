import { useRef, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useGroups } from '../lib/queries'
import { Icon, MAIN_TABS, MainNav, Sheet, mainTabIndex, useGoToTab } from '../ui'
import { useSwipeTabs } from '../ui/swipeTabs'
import { NewGroupSheet } from './NewGroup'

/**
 * Layout of the four top-level pages (Groups, Friends, Activity, Profile): the page, the bottom bar, and the Add sheets
 * the bar opens. Other pages (a group, the expense form, an invite) have their own back button and actions, so the bar
 * is not shown there.
 */
export function AppShell() {
  const { pathname } = useLocation()
  const nav = useNavigate()
  const { data: groups } = useGroups()
  const [menu, setMenu] = useState(false)
  const [creating, setCreating] = useState(false)
  const [picking, setPicking] = useState(false)

  // Moving between tabs (tapping the bar or swiping) slides the new page in from the side it sits on. Coming back from a
  // group, or opening the app, keeps the plain fade: only a move from one tab to another slides.
  const tab = mainTabIndex(pathname)
  const goTo = useGoToTab()
  useSwipeTabs(tab, MAIN_TABS.length, goTo)
  // decided once per navigation, so a re-render during the 0.34s slide cannot cut it short
  const last = useRef({ path: pathname, tab, slide: '' })
  if (last.current.path !== pathname) {
    const from = last.current.tab
    last.current = { path: pathname, tab, slide: from >= 0 && tab >= 0 ? (tab > from ? 'animate-tab-fwd' : 'animate-tab-back') : '' }
  }
  const slide = last.current.slide

  return (
    <>
      <div key={pathname} className={slide}><Outlet /></div>
      {mainTabIndex(pathname) >= 0 && <MainNav onAdd={() => setMenu(true)} />}

      <Sheet open={menu} onClose={() => setMenu(false)}>
        <div className="space-y-3">
          <button type="button" className="glass-btn flex w-full items-center gap-3 rounded-full px-5 py-3.5 font-semibold"
            onClick={() => { setMenu(false); setCreating(true) }}>
            <Icon name="groups" /> New group
          </button>
          <button type="button" className="glass-btn flex w-full items-center gap-3 rounded-full px-5 py-3.5 font-semibold disabled:opacity-45"
            disabled={!groups?.length}
            onClick={() => { setMenu(false); setPicking(true) }}>
            <Icon name="receipt" /> Add expense
          </button>
        </div>
      </Sheet>

      <Sheet open={picking} onClose={() => setPicking(false)} title="Add expense to which group?">
        <div className="max-h-[50dvh] space-y-3 overflow-y-auto">
          {groups?.map((g) => (
            <button key={g.id} type="button" className="glass-btn flex w-full items-center gap-3 rounded-full px-5 py-3.5 text-left font-semibold"
              onClick={() => { setPicking(false); nav(`/groups/${g.id}/expense/new`) }}>
              <Icon name="groups" /> <span className="min-w-0 flex-1 truncate">{g.name}</span>
            </button>
          ))}
        </div>
      </Sheet>

      <NewGroupSheet open={creating} onClose={() => setCreating(false)} />
    </>
  )
}
