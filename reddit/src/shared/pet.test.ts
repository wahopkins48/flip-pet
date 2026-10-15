import assert from 'node:assert/strict'
import {test} from 'node:test'
import {
  ASCEND_SECONDS,
  act,
  advance,
  depart,
  freshPet,
  isOver,
  type Meters,
  OVERFED,
  type PetState,
  randomName,
  SPECIES,
  tick,
  view,
} from './pet.ts'
import {JavaRandom} from './random.ts'

function near(a: number, b: number, why: string): void {
  assert.ok(Math.abs(a - b) < 0.00001, `${why}: ${a} != ${b}`)
}

const fresh: Meters = [70, 70, 80, 75]
const T0 = 1_760_140_800

function pet(over: Partial<PetState> = {}): PetState {
  return {...freshPet(T0, 1, false), ...over}
}

// The phone's PetRulesTest, check for check: the rules must not drift.

test('survival hunger timeline matches the phone', () => {
  let r = advance(fresh, false, false, true, 0, 18000)
  assert.ok(!r.dead, 'food empties at five hours; death comes later')
  near(r.neglect, 0, 'grace begins at crossing')
  r = advance(fresh, false, false, true, 0, 18899)
  assert.ok(!r.dead, 'survives until whole grace elapses')
  near(r.neglect, 899, 'actual empty time only')
  r = advance(fresh, false, false, true, 0, 18900)
  assert.ok(r.dead && r.cause === 0, 'hunger death at 5h15')
  near(r.advanced, 18900, 'stops at death')
  const late = advance(fresh, false, false, true, 0, 7 * 86400)
  near(late.advanced, r.advanced, 'week away has same death time')
  for (let i = 0; i < 4; i++)
    near(late.meters[i]!, r.meters[i]!, 'dead meters frozen')
})

test('polling rate does not change the outcome', () => {
  const once = advance(fresh, false, true, true, 0, 4 * 3600)
  let meters = fresh
  let neglect = 0
  let small = advance(meters, false, true, true, neglect, 0)
  for (let i = 0; i < 2880; i++) {
    small = advance(meters, false, true, true, neglect, 5)
    meters = small.meters
    neglect = small.neglect
    if (small.dead) break
  }
  assert.equal(once.dead, small.dead, 'polling does not change survival')
  for (let i = 0; i < 4; i++)
    near(once.meters[i]!, small.meters[i]!, 'polling invariant')
})

test('gentle care runs at quarter speed with six hours of grace', () => {
  let r = advance(fresh, false, false, false, 0, 26 * 3600 - 1)
  assert.ok(!r.dead, 'gentle survives a day away')
  near(r.neglect, 6 * 3600 - 1, 'gentle grace tracked')
  r = advance(fresh, false, false, false, 0, 30 * 86400)
  assert.ok(r.dead && r.cause === 0, 'gentle still starves eventually')
  near(r.advanced, 26 * 3600, 'gentle death at 26h')
  r = advance(fresh, false, false, false, 0, 4 * 3600)
  near(r.meters[0], 56, 'quarter-speed gentle decay')
})

test('sleep, grace and sickness edge cases match the phone', () => {
  let r = advance([50, 50, 0, 50], true, false, true, 880, 60)
  assert.ok(!r.dead && r.meters[2] > 0, 'sleep rescues zero vitality')
  near(r.neglect, 0, 'recovery resets neglect')
  r = advance([0, 50, 0, 50], true, false, true, 880, 60)
  assert.ok(r.dead && r.cause === 0, 'sleep cannot rescue starvation')
  near(r.advanced, 20, 'existing grace preserved')
  r = advance([50, 0, 50, 50], false, false, true, 60, 60)
  assert.ok(!r.dead && r.cause === 1, 'empty joy tracked')
  near(r.neglect, 120, 'grace persists')
  r = advance(fresh, false, true, true, 0, 3600)
  near(r.meters[2], 64, 'double sick vitality loss')
  near(r.meters[3], 57, 'double sick salt loss')
  r = advance(fresh, true, true, true, 0, 3600)
  near(r.meters[2], 93, 'sleep recovery not doubled')
  near(fresh[0], 70, 'input not mutated')
})

// Actions, as LocalPet.act applies them.

test('feeding feeds, and overfeeding sickens', () => {
  let {state, said} = act(pet({meters: [40, 50, 50, 50]}), 'feed', T0)
  assert.deepEqual(state.meters, [78, 54, 50, 50])
  assert.equal(said, 'it eats. the room gets quieter')
  assert.ok(!state.sick)
  ;({state, said} = act(pet({meters: [OVERFED + 1, 50, 50, 50]}), 'feed', T0))
  assert.ok(state.sick, 'above 85 FED it sickens')
  assert.equal(state.meters[0], OVERFED + 1, 'and it is not fed further')
})

test('only fresh salt to 95 cures sickness', () => {
  let {state} = act(pet({sick: true, meters: [50, 50, 50, 50]}), 'ward', T0)
  assert.ok(state.sick, '85 SALT is not enough')
  ;({state} = act(state, 'ward', T0))
  assert.ok(!state.sick, 'warding up to 95 cures it')
  ;({state} = act(pet({sick: true, meters: [50, 50, 50, 100]}), 'feed', T0))
  assert.ok(state.sick, 'salt already down does not cure a new bout')
})

test('feed and play wake it; ward lets it sleep', () => {
  const asleep = pet({asleep: true})
  assert.ok(!act(asleep, 'feed', T0).state.asleep)
  assert.ok(!act(asleep, 'play', T0).state.asleep)
  assert.ok(act(asleep, 'ward', T0).state.asleep)
  assert.ok(act(pet(), 'sleep', T0).state.asleep)
  assert.ok(!act(asleep, 'wake', T0).state.asleep)
})

test('play trades food and rest for joy', () => {
  const {state} = act(pet({meters: [50, 50, 50, 50]}), 'play', T0)
  assert.deepEqual(state.meters, [42, 80, 38, 50])
})

test('care is counted and logged without anyone in it', () => {
  let s = pet()
  for (let i = 0; i < 9; i++) s = act(s, 'ward', T0 + i).state
  assert.equal(s.tends, 9)
  assert.equal(s.log.length, 6, 'the log keeps only the latest few')
  assert.deepEqual(Object.keys(s.log[0]!).sort(), ['a', 'said', 't'])
  assert.equal(s.log[0]!.t, T0 + 8, 'newest first')
})

// The Reddit arc: one egg, three days, one ending.

test('it ascends as an Elder god at 72 hours and stops there', () => {
  const s = tick(
    pet({meters: [100, 100, 100, 100], last: T0 + ASCEND_SECONDS - 3600}),
    T0 + ASCEND_SECONDS + 3600,
  )
  assert.ok(s.ascended && isOver(s))
  assert.equal(s.last, T0 + ASCEND_SECONDS, 'time stops at ascension')
  const v = view(s, T0 + ASCEND_SECONDS + 86400)
  assert.equal(v.stageName, 'Elder god')
  assert.equal(v.ageLabel, '3 d', 'its age stays at its ascension')
  assert.equal(
    act(s, 'feed', T0 + ASCEND_SECONDS + 7200).said,
    'it is beyond your care now',
  )
})

test('a survival death freezes age, meters and cause', () => {
  const s = tick(pet({survival: true}), T0 + 86400)
  assert.ok(s.dead && !s.ascended)
  assert.equal(s.deadAt, T0 + 18900, 'starved at 5h15, as on the phone')
  const v = view(s, T0 + 2 * 86400)
  assert.equal(v.stageName, 'Writhing thing')
  assert.match(v.mood, /starved/)
  assert.equal(act(s, 'feed', T0 + 86400).said, 'there is nothing left to feed')
})

test('a neglected gentle creature dies; a tended one ascends', () => {
  const neglected = tick(pet(), T0 + ASCEND_SECONDS)
  assert.ok(neglected.dead && !neglected.ascended)
  assert.equal(neglected.deadAt, T0 + 26 * 3600, 'starved at 26h')
  let s = pet()
  for (let t = T0 + 12 * 3600; t < T0 + ASCEND_SECONDS; t += 12 * 3600) {
    s = tick(s, t)
    s.meters = [...fresh]
  }
  s = tick(s, T0 + ASCEND_SECONDS)
  assert.ok(s.ascended && !s.dead)
})

test('a creature let go by a moderator departs at that age', () => {
  const s = depart(pet(), T0 + 5 * 3600)
  assert.ok(isOver(s) && !s.dead && !s.ascended)
  assert.equal(view(s, T0 + 86400).stageName, 'Writhing thing')
  assert.equal(depart(s, T0 + 86400).departedAt, s.departedAt, 'departs once')
})

test('time running backwards neither de-ages it nor double charges', () => {
  const s = tick(pet({last: T0 + 600}), T0)
  assert.equal(s.born, T0 - 600)
  assert.deepEqual(s.meters, fresh)
})

test('views: hatching, danger and countdown', () => {
  const half = view(pet(), T0 + 1800)
  assert.equal(half.stage, 0)
  near(half.hatchProgress, 0.5, 'the egg cracks over its first hour')
  assert.equal(view(pet(), T0 + 3600).stageName, 'Hatchling')
  const empty = tick(pet({survival: true, meters: [0, 50, 50, 50]}), T0 + 120)
  const v = view(empty, T0 + 120)
  assert.ok(v.danger)
  assert.equal(v.graceMinutes, 13)
  assert.equal(view(pet(), T0).ascendsIn, ASCEND_SECONDS)
})

test('the mood line holds steady within the hour', () => {
  const s = pet()
  assert.equal(view(s, T0 + 60).mood, view(s, T0 + 3000).mood)
})

// Seeds have to give the phone's results, or the creatures differ.

test('JavaRandom matches java.util.Random', () => {
  // Generated with java.util.Random on Temurin 17.
  const want: [number, number, number, number, number, number][] = [
    [0, 0, 13, 9, 0.6063452, 0.6374174253501083],
    [42, 2, 0, 8, 0.0479393, 0.30871945533265976],
    [1760140800, 1, 0, 8, 0.96142477, 0.9182812189480531],
    [-7, 4, 14, 0, 0.9458454, 0.5214896372918163],
  ]
  for (const [seed, a, b, c, f, d] of want) {
    const r = new JavaRandom(seed)
    assert.deepEqual([r.nextInt(6), r.nextInt(16), r.nextInt(10)], [a, b, c])
    near(r.nextFloat(), f, `float for ${seed}`)
    assert.equal(r.nextDouble(), d)
  }
})

test('every egg gets a dictionary name and a real species', () => {
  for (let i = 0; i < 200; i++) {
    const s = freshPet(T0 + i, i, false)
    assert.match(s.name, /^[A-Z][a-z]+$/)
    assert.ok(s.phenotype >= 0 && s.phenotype < SPECIES.length)
  }
  assert.equal(randomName(42), randomName(42))
})
