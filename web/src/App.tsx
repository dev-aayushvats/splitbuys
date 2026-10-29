import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './lib/auth'
import { OfflineBanner, Screen, Spinner } from './ui'
import { GroupsScreen } from './screens/Groups'
import { GroupScreen } from './screens/Group'
import { JoinScreen } from './screens/Join'
import { LoginScreen } from './screens/Login'
import { NameScreen } from './screens/Name'
import { ProfileScreen } from './screens/Profile'
import { ExpenseFormScreen } from './screens/ExpenseForm'

/** Sign-in → name → app. Remembers where the user was headed via ?next=. */
function Gate({ page }: { page: 'login' | 'name' | 'app' }) {
  const { user, ready } = useAuth()
  const loc = useLocation()
  if (!ready) return <Screen><div className="grid flex-1 place-items-center"><Spinner /></div></Screen>

  const here = loc.pathname + loc.search
  const next = new URLSearchParams(loc.search).get('next')
  const withNext = (p: string) => (here === '/' ? p : `${p}?next=${encodeURIComponent(here)}`)
  const hasName = !!user?.name

  if (!user) return page === 'login' ? <Outlet /> : <Navigate to={withNext('/login')} replace />
  if (!hasName) return page === 'name' ? <Outlet /> : <Navigate to={withNext('/name')} replace />
  if (page !== 'app') return <Navigate to={next ?? '/'} replace />
  return <Outlet />
}

export function App() {
  return (
    <>
    <OfflineBanner />
    <Routes>
      <Route element={<Gate page="login" />}><Route path="/login" element={<LoginScreen />} /></Route>
      <Route element={<Gate page="name" />}><Route path="/name" element={<NameScreen />} /></Route>
      <Route element={<Gate page="app" />}>
        <Route path="/" element={<GroupsScreen />} />
        <Route path="/profile" element={<ProfileScreen />} />
        <Route path="/join/:token" element={<JoinScreen />} />
        <Route path="/groups/:id" element={<GroupScreen />} />
        <Route path="/groups/:id/expense/new" element={<ExpenseFormScreen />} />
        <Route path="/groups/:id/expense/:expenseId" element={<ExpenseFormScreen />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </>
  )
}
