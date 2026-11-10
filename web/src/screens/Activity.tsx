import { Dashed, EmptyState, Screen } from '../ui'

export function ActivityScreen() {
  return (
    <Screen className="pt-safe pb-safe">
      <div className="space-y-6 px-5 pb-32 pt-4">
        <h1 className="text-2xl font-extrabold">Activity</h1>
        <Dashed><EmptyState icon="activity" title="Coming soon">Every expense and payment that involves you, across all your groups, will be listed here.</EmptyState></Dashed>
      </div>
    </Screen>
  )
}
