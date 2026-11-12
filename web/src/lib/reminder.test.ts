import { expect, it } from 'vitest'
import { reminderMessage } from './reminder'

it('names the person, the amount and the group', () => {
  expect(reminderMessage('Bo', 25000, 'Goa trip')).toBe('Hi Bo, a gentle reminder: you owe ₹250.00 in “Goa trip” on SplitBuys.')
})
