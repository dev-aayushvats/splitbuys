import { describe, expect, it } from 'vitest'
import { evaluateSplit } from './splits'

const base = { amount: '100', description: 'Dinner', kind: 'EQUAL' as const, ids: [1, 2], values: {}, nameOf: (id: number) => `U${id}` }

describe('evaluateSplit', () => {
  it('requires a description, a valid amount and someone selected', () => {
    expect(evaluateSplit({ ...base, description: ' ' }).problem).toBe('Add a description')
    expect(evaluateSplit({ ...base, amount: '0' }).problem).toBe('Enter a valid amount')
    expect(evaluateSplit({ ...base, amount: '1.234' }).problem).toBe('Enter a valid amount')
    expect(evaluateSplit({ ...base, ids: [] }).problem).toBe('Pick at least one person')
  })

  it('splits equally', () => {
    const r = evaluateSplit(base)
    expect(r.splits).toEqual([{ userId: 1 }, { userId: 2 }])
    expect(r.hint).toBe('₹50.00 each')
  })

  it('treats empty shares as 1 and rejects non-integers', () => {
    expect(evaluateSplit({ ...base, kind: 'SHARES', values: { 1: '2' } }).splits).toEqual([
      { userId: 1, value: 2 }, { userId: 2, value: 1 },
    ])
    expect(evaluateSplit({ ...base, kind: 'SHARES', values: { 1: '1.5' } }).problem).toBe('Shares must be whole numbers')
    expect(evaluateSplit({ ...base, kind: 'SHARES', values: { 1: '0', 2: '0' } }).problem).toBe('At least one share must be above zero')
  })

  it('reports what is left or over for exact amounts', () => {
    expect(evaluateSplit({ ...base, kind: 'EXACT', values: { 1: '30' } }).problem).toBe('₹70.00 left to assign')
    expect(evaluateSplit({ ...base, kind: 'EXACT', values: { 1: '80', 2: '40' } }).problem).toBe('Over by ₹20.00')
    expect(evaluateSplit({ ...base, kind: 'EXACT', values: { 1: '60', 2: '40' } }).splits).toEqual([
      { userId: 1, value: 6000 }, { userId: 2, value: 4000 },
    ])
  })

  it('needs percentages to total 100 and sends them as percent', () => {
    expect(evaluateSplit({ ...base, kind: 'PERCENT', values: { 1: '70' } }).problem).toBe('30% left to assign')
    expect(evaluateSplit({ ...base, kind: 'PERCENT', values: { 1: '70', 2: '30' } }).splits).toEqual([
      { userId: 1, value: 70 }, { userId: 2, value: 30 },
    ])
  })

  it('names the person whose number is malformed', () => {
    expect(evaluateSplit({ ...base, kind: 'EXACT', values: { 2: 'abc' } }).problem).toBe('Check the number for U2')
  })
})
