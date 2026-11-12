import type { ExpenseCategory } from './types'

/**
 * Guesses a category from what the person typed. It is a suggestion the person can change, so it favours being right over
 * guessing: no match (or a tie) returns null rather than a wrong category.
 *
 * Order of trust: (1) what this group already filed under the same description, (2) keywords and short phrases, whole words
 * first and then a one- or two-letter typo allowance for longer words.
 */

type Guessable = Exclude<ExpenseCategory, 'OTHER'>

const WORDS: Record<Guessable, string> = {
  FOOD: `dinner lunch breakfast brunch snack snacks food meal meals restaurant restaurants cafe coffee tea chai pizza burger biryani thali
    swiggy zomato dominos mcdonalds kfc starbucks drinks beer wine bar pub icecream dessert cake dhaba tiffin lassi sandwich maggi momos
    pasta noodles paratha dosa idli samosa chaat juice bakery eatery`,
  GROCERIES: `groceries grocery vegetables veggies fruits milk bigbasket blinkit zepto instamart supermarket kirana dmart eggs bread atta
    rice dal provisions`,
  TRAVEL: `uber ola rapido cab cabs taxi auto rickshaw petrol diesel fuel toll parking flight flights train irctc bus metro redbus
    makemytrip airline airport ferry fastag scooter`,
  STAY: `hotel hostel airbnb stay resort homestay villa lodge oyo accommodation`,
  UTILITIES: `rent electricity electric water bill bills wifi internet broadband recharge gas cylinder lpg maintenance dth jio airtel
    vodafone`,
  ENTERTAINMENT: `movie movies cinema pvr film netflix spotify prime hotstar concert show game games gaming bowling club party karaoke
    museum`,
  SHOPPING: `shopping clothes clothing shirt shoes dress amazon flipkart myntra gift gifts present mall electronics laptop headphones`,
}

/** Phrases outrank single words ("uber eats" is food, though "uber" alone is a cab). */
const PHRASES: [string, Guessable][] = [
  ['uber eats', 'FOOD'], ['eating out', 'FOOD'], ['ice cream', 'FOOD'], ['house rent', 'UTILITIES'], ['flat rent', 'UTILITIES'],
  ['car rent', 'TRAVEL'], ['bike rent', 'TRAVEL'], ['road trip', 'TRAVEL'], ['movie ticket', 'ENTERTAINMENT'],
  ['water park', 'ENTERTAINMENT'], ['theme park', 'ENTERTAINMENT'], ['reliance fresh', 'GROCERIES'], ['big basket', 'GROCERIES'], ['room rent', 'UTILITIES'],
]

const INDEX = new Map<string, Guessable>()
for (const [category, words] of Object.entries(WORDS) as [Guessable, string][]) {
  for (const w of words.split(/\s+/).filter(Boolean)) INDEX.set(w, category)
}

const normalize = (text: string) => text.toLowerCase().replace(/[^a-z\s]/g, ' ').replace(/\s+/g, ' ').trim()

/** Edit distance, stopping early once it must exceed `max`. */
function within(a: string, b: string, max: number): boolean {
  if (Math.abs(a.length - b.length) > max) return false
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j)
  for (let i = 1; i <= a.length; i++) {
    const cur = [i]
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
    }
    if (Math.min(...cur) > max) return false
    prev = cur
  }
  return prev[b.length] <= max
}

function categoryOfWord(word: string): Guessable | null {
  const exact = INDEX.get(word)
  if (exact) return exact
  if (word.length < 5) return null
  const allowed = word.length >= 8 ? 2 : 1 // a longer word can carry a bigger typo before it could be a different word
  let found: Guessable | null = null
  for (const [known, category] of INDEX) {
    if (known.length < 5 || !within(word, known, allowed)) continue
    if (found && found !== category) return null // two different categories are equally close: do not guess
    found = category
  }
  return found
}

export interface Suggestion {
  category: ExpenseCategory
  /** Where it came from, so the screen can say so honestly. */
  source: 'history' | 'words'
}

export function suggestCategory(description: string, earlier: { description: string; category: ExpenseCategory }[] = []): Suggestion | null {
  const text = normalize(description)
  if (text.length < 3) return null

  const same = earlier.find((e) => e.category !== 'OTHER' && normalize(e.description) === text)
  if (same) return { category: same.category, source: 'history' }

  const score = new Map<Guessable, number>()
  const add = (c: Guessable, n: number) => score.set(c, (score.get(c) ?? 0) + n)
  let rest = ` ${text} `
  for (const [phrase, category] of PHRASES) {
    if (rest.includes(` ${phrase} `)) { add(category, 2); rest = rest.replace(` ${phrase} `, ' ') }
  }
  for (const word of rest.split(' ').filter((w) => w.length >= 3)) {
    const category = categoryOfWord(word)
    if (category) add(category, 1)
  }
  const ranked = [...score.entries()].sort((a, b) => b[1] - a[1])
  if (ranked.length === 0 || (ranked.length > 1 && ranked[0][1] === ranked[1][1])) return null
  return { category: ranked[0][0], source: 'words' }
}
