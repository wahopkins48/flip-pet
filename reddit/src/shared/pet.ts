/**
 * The creature itself: four meters, six stages, and a mood line. Pure functions
 * of a stored state and a clock, shared by the server (which owns the truth)
 * and the client (which keeps the meters moving between updates).
 *
 * There is no tick loop. Every read folds however long it has been since the
 * last touch into the saved numbers, exactly as the phone version does.
 */
import {JavaRandom} from './random.ts'

export const GRACE_SECONDS = 15 * 60
/** Gentle drains slowly and forgives a lot, but a meter left empty all day is fatal. */
export const GENTLE_GRACE_SECONDS = 6 * 3600

export function grace(survival: boolean): number {
  return survival ? GRACE_SECONDS : GENTLE_GRACE_SECONDS
}
/** Per-hour change at Survival speed: fed, joy, vitality, wards. */
const AWAKE = [14, 10, 8, 9] as const
const ASLEEP = [7, 3, -13, 5] as const

/** Hours of age at which the creature stops being what it was. */
export const STAGE_HOURS = [0, 1, 4, 12, 30, 72] as const
export const STAGE_NAMES = [
  'Egg, unthought',
  'Hatchling',
  'Writhing thing',
  'Choir of small mouths',
  'Abyssal archon',
  'Elder god',
] as const
/** The egg cracks over its first hour. */
export const HATCH_HOURS = 1
/** On Reddit the life is one three-day arc: an Elder god ascends and is done. */
export const ASCEND_SECONDS = STAGE_HOURS[5] * 3600

/** What hatches out. All six come from the same egg, chosen the moment it begins. */
export const SPECIES = [
  'Amber Child',
  'Pale Host',
  'The Sleet',
  'Red Crown',
  'Drowned Lamp',
  'Old Harvest',
] as const

/** Sickness only lifts once fresh salt has been laid on properly. */
export const WARD_CURE = 95
/** Feeding above this makes it sick instead of fed. */
export const OVERFED = 85

const NAME_HEADS = [
  'Vur',
  'Xoth',
  'Gla',
  'Ny',
  'Zer',
  'Mor',
  'Ith',
  'Qua',
  'Bra',
  'Sel',
  'Kha',
  'Vex',
  'Oth',
  'Ygg',
  'Ur',
  'Tz',
] as const
const NAME_TAILS = [
  'ith',
  'or',
  'un',
  'ax',
  'esh',
  'og',
  'ir',
  'um',
  'al',
  'oth',
] as const

const CAUSES = [
  'starved. It forgot what mouths were for.',
  'unhappy. It stopped pretending, and then stopped.',
  'exhausted. It lay down and became architecture.',
  'unwarded. Something else came through the gap first.',
] as const

const MOOD = {
  good: [
    'it watches you, and permits it',
    'content, in the way a trap is content',
    'it purrs in a register you feel in your teeth',
    'it is being good at you',
  ],
  lonely: [
    'it has not seen a face in some time',
    'it is rehearsing your name badly',
    'the loneliness has developed opinions',
  ],
  hungry: [
    'it is thinking about the moon, or something under the moon',
    'it has started chewing the floor',
    'it wants to be fed and it wants it now',
  ],
  dirty: [
    'the salt has gone clotted',
    'there is a smell coming off it that has a direction',
    'it is attracting the small attentions',
  ],
  tired: [
    'it is running on the memory of sleep',
    'it keeps sitting down halfway through standing up',
    'it needs to lie down in the dark for a while',
  ],
  sick: [
    'it is accruing paradoxes',
    'two of it are arguing about which one of them is real',
    'it has a wound that opens when it is looked at',
  ],
  asleep: [
    'it dreams, which is a kind of risk',
    'sleeping. quietly. so far',
    'it is elsewhere and not being quiet about it',
  ],
  dead: [
    'it is gone to the Others',
    'there is nothing here now',
    'at rest, at last',
  ],
  departed: [
    'it went back to the Others',
    'it left the way it came, through the back of things',
    'somewhere else is keeping it now',
  ],
  ascended: [
    'it has ascended. it remembers every one of you',
    'it is too large to be here now. it left a shape behind',
    'it went up, and something up there moved over',
  ],
} as const

export type Action = 'feed' | 'play' | 'ward' | 'sleep' | 'wake'
export const ACTIONS: readonly Action[] = [
  'feed',
  'play',
  'ward',
  'sleep',
  'wake',
]

/** The four meters, all "higher is better", matching the four buttons. */
export type Meters = [fed: number, joy: number, vitality: number, wards: number]

/** One community action, with no record of who did it. */
export type LogEntry = {t: number; a: Action; said: string}

export type PetState = {
  v: 1
  name: string
  /** Unix seconds. */
  born: number
  meters: Meters
  asleep: boolean
  sick: boolean
  dead: boolean
  deadAt: number
  cause: string
  survival: boolean
  /** Seconds a meter has sat at zero, for the Survival grace period. */
  neglect: number
  /** Unix seconds of the last fold of time into the meters. */
  last: number
  generation: number
  phenotype: number
  ascended: boolean
  /** Unix seconds a moderator let it go before its time; 0 if never. */
  departedAt: number
  /** Total care actions by the community. */
  tends: number
  log: LogEntry[]
}

const LOG_LENGTH = 6

export type AdvanceResult = {
  meters: Meters
  neglect: number
  dead: boolean
  cause: number
  advanced: number
}

/** Pure time simulation: reading often or after a day produces the same result. */
export function advance(
  meters: Readonly<Meters>,
  asleep: boolean,
  sick: boolean,
  survival: boolean,
  neglect: number,
  seconds: number,
): AdvanceResult {
  const rates = [...(asleep ? ASLEEP : AWAKE)]
  let firstEmpty = Number.POSITIVE_INFINITY
  let cause = -1
  for (let i = 0; i < rates.length; i++) {
    if (sick && i >= 2 && rates[i]! > 0) rates[i]! *= 2
    if (!survival) rates[i]! *= 0.25
    // A zero meter which is recovering (sleeping vitality) is not neglected.
    if (rates[i]! > 0) {
      const emptyAt = (Math.max(0, meters[i]!) / rates[i]!) * 3600
      if (emptyAt < firstEmpty) {
        firstEmpty = emptyAt
        cause = i
      }
    }
  }
  const carried = firstEmpty === 0 ? Math.max(0, neglect) : 0
  const deathAt = firstEmpty + Math.max(0, grace(survival) - carried)
  const dead = seconds >= deathAt
  const advanced = Math.max(0, dead ? deathAt : seconds)
  const result = meters.map((m, i) =>
    clamp(m - (rates[i]! * advanced) / 3600),
  ) as Meters
  const emptySeconds =
    advanced >= firstEmpty ? carried + advanced - firstEmpty : 0
  return {meters: result, neglect: emptySeconds, dead, cause, advanced}
}

export function clamp(v: number): number {
  return Math.max(0, Math.min(100, v))
}

/** A name from the fixed dictionary, so nobody ever types one in. */
export function randomName(seed: number): string {
  const rng = new JavaRandom(seed)
  return (
    NAME_HEADS[rng.nextInt(NAME_HEADS.length)]! +
    NAME_TAILS[rng.nextInt(NAME_TAILS.length)]!
  )
}

export function freshPet(
  now: number,
  generation: number,
  survival: boolean,
): PetState {
  return {
    v: 1,
    name: randomName(now * 31 + generation),
    born: now,
    meters: [70, 70, 80, 75],
    asleep: false,
    sick: false,
    dead: false,
    deadAt: 0,
    cause: '',
    survival,
    neglect: 0,
    last: now,
    generation,
    phenotype: new JavaRandom(now).nextInt(SPECIES.length),
    ascended: false,
    departedAt: 0,
    tends: 0,
    log: [],
  }
}

/** True once there is nothing left to care for: dead, gone up, or let go. */
export function isOver(s: Readonly<PetState>): boolean {
  return s.dead || s.ascended || s.departedAt > 0
}

/** Ends a living creature early, by moderator choice. */
export function depart(state: Readonly<PetState>, now: number): PetState {
  const s = tick(state, now)
  if (isOver(s)) return s
  return {...s, departedAt: s.last, asleep: false}
}

/**
 * Folds elapsed time into the meters, up to the moment of death or ascension.
 * Returns a new state; the input is untouched.
 */
export function tick(state: Readonly<PetState>, now: number): PetState {
  const s = structuredClone(state) as PetState
  if (isOver(s)) return s
  // A clock correction backwards must not double-charge time later or de-age it.
  if (now < s.last) {
    s.born += now - s.last
    s.last = now
    return s
  }
  const end = s.born + ASCEND_SECONDS
  const until = Math.min(now, end)
  const from = s.last
  const r = advance(
    s.meters,
    s.asleep,
    s.sick,
    s.survival,
    s.neglect,
    Math.max(0, until - from),
  )
  s.meters = r.meters
  s.neglect = r.neglect
  s.last = until
  if (r.dead) {
    s.dead = true
    s.deadAt = from + Math.ceil(r.advanced)
    s.cause = CAUSES[r.cause] ?? 'ended, for reasons unrecorded'
    s.asleep = false
  } else if (until >= end) {
    s.ascended = true
    s.asleep = false
  }
  return s
}

function anyEmpty(s: Readonly<PetState>): boolean {
  return s.meters.some(m => m <= 0)
}

/**
 * Applies one community action at `now`. Returns the new state and the line
 * the creature answers with. Wrong actions are allowed: overfeeding is how
 * a crowd hurts the thing it is trying to love.
 */
export function act(
  state: Readonly<PetState>,
  action: Action,
  now: number,
): {state: PetState; said: string} {
  const s = tick(state, now)
  if (s.dead) return {state: s, said: `there is nothing left to ${action}`}
  if (isOver(s)) return {state: s, said: 'it is beyond your care now'}

  const [fed, joy, vitality, wards] = s.meters
  const woke = s.asleep && (action === 'feed' || action === 'play')
  if (woke) s.asleep = false

  let said: string
  switch (action) {
    case 'feed':
      if (fed > OVERFED) {
        s.sick = true
        said = woke
          ? 'it wakes, overeats, and sickens'
          : 'too much. it is keeping the extra'
      } else {
        s.meters = [clamp(fed + 38), clamp(joy + 4), vitality, wards]
        said = woke
          ? 'it wakes to eat. the room quiets'
          : 'it eats. the room gets quieter'
      }
      break
    case 'play':
      s.meters = [clamp(fed - 8), clamp(joy + 30), clamp(vitality - 12), wards]
      said = woke
        ? 'it wakes to play. you regret it'
        : 'it plays. you regret starting this'
      break
    case 'ward':
      s.meters = [fed, clamp(joy - 4), vitality, clamp(wards + 35)]
      said = 'the salt holds. for now'
      if (s.sick && s.meters[3] >= WARD_CURE) {
        s.sick = false
        said = 'fresh salt. only one of it remains'
      }
      break
    case 'sleep':
      if (s.asleep) said = 'it is already asleep'
      else {
        s.asleep = true
        said = 'it curls up and stops looking at you'
      }
      break
    case 'wake':
      if (!s.asleep) said = 'it is already awake and watching'
      else {
        s.asleep = false
        said = 'it opens every eye at once'
      }
      break
  }

  s.last = now
  if (!anyEmpty(s)) s.neglect = 0
  s.tends++
  s.log = [{t: now, a: action, said}, ...s.log].slice(0, LOG_LENGTH)
  return {state: s, said}
}

export type PetView = {
  stage: number
  stageName: string
  species: string
  ageHours: number
  ageLabel: string
  hatchProgress: number
  mood: string
  /** A meter is empty and the grace period is running. */
  danger: boolean
  graceMinutes: number
  /** Seconds until ascension, while it lives. */
  ascendsIn: number
}

export function stageFor(ageHours: number): number {
  let stage = 0
  for (let i = 0; i < STAGE_HOURS.length; i++) {
    if (ageHours >= STAGE_HOURS[i]!) stage = i
  }
  return stage
}

export function ageLabel(ageHours: number): string {
  if (ageHours < 1) return `${Math.max(0, Math.floor(ageHours * 60))} min`
  if (ageHours < 48) return `${Math.floor(ageHours)} h`
  return `${Math.floor(ageHours / 24)} d`
}

function moodLines(s: Readonly<PetState>): readonly string[] {
  if (s.dead) return MOOD.dead
  if (s.ascended) return MOOD.ascended
  if (s.departedAt) return MOOD.departed
  if (s.asleep) return MOOD.asleep
  if (s.sick) return MOOD.sick
  const [fed, joy, vitality, wards] = s.meters
  if (fed <= 20) return MOOD.hungry
  if (wards <= 20) return MOOD.dirty
  if (vitality <= 20) return MOOD.tired
  if (joy <= 20) return MOOD.lonely
  return MOOD.good
}

/** Derived display values. Call on a state already ticked to `now`. */
export function view(s: Readonly<PetState>, now: number): PetView {
  const at = s.dead
    ? s.deadAt
    : s.ascended
      ? s.born + ASCEND_SECONDS
      : s.departedAt || now
  const ageHours = Math.max(0, (at - s.born) / 3600)
  const stage = stageFor(ageHours)
  const lines = moodLines(s)
  // Stable per pet per hour, so repeated refreshes do not shuffle the line.
  const seed = Math.floor(s.born / 3600) + Math.floor(now / 3600)
  const mood = lines[new JavaRandom(seed).nextInt(lines.length)]!
  const species = SPECIES[mod(s.phenotype, SPECIES.length)]!
  return {
    stage,
    stageName: STAGE_NAMES[stage]!,
    species,
    ageHours,
    ageLabel: ageLabel(ageHours),
    hatchProgress: stage === 0 ? Math.min(1, ageHours / HATCH_HOURS) : 1,
    mood: s.dead ? `${s.name} ${s.cause}` : mood,
    danger: !isOver(s) && anyEmpty(s),
    graceMinutes: Math.max(0, Math.ceil((grace(s.survival) - s.neglect) / 60)),
    ascendsIn: isOver(s) ? 0 : Math.max(0, s.born + ASCEND_SECONDS - now),
  }
}

export function mod(n: number, m: number): number {
  return ((n % m) + m) % m
}
