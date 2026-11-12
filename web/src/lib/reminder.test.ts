import { expect, it } from 'vitest'
import { reminderMessage } from './reminder'

it('names the person, the amount and the group', () => {
  expect(reminderMessage('Bo', 25000, 'Goa trip')).toBe('Hi Bo, a gentle reminder: you owe ₹250.00 in “Goa trip” on SplitBuys.')
})

import { settleUpMessage } from './reminder'

it('lists who pays whom, one line each, with a footer', () => {
  expect(settleUpMessage('Goa trip', [{ from: 'Bo', to: 'Asha', amountPaise: 40000 }, { from: 'Cy', to: 'Asha', amountPaise: 25050 }]))
    .toBe('“Goa trip”: who pays whom\n• Bo pays Asha ₹400.00\n• Cy pays Asha ₹250.50\nTracked on SplitBuys')
})
it('says so when nobody owes anything', () => {
  expect(settleUpMessage('Goa trip', [])).toBe('Everyone is settled up in “Goa trip”.')
})
