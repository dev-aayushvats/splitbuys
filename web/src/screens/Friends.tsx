import { Dashed, EmptyState, Screen } from '../ui'

export function FriendsScreen() {
  return (
    <Screen className="pt-safe pb-safe">
      <div className="space-y-6 px-5 pb-32 pt-4">
        <h1 className="text-2xl font-extrabold">Friends</h1>
        <Dashed><EmptyState icon="friends" title="Coming soon">People you share a group with will show up here, with what you owe each other.</EmptyState></Dashed>
      </div>
    </Screen>
  )
}
