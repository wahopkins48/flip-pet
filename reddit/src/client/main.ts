import {
  connectRealtime,
  context,
  navigateTo,
  showLoginPrompt,
} from '@devvit/web/client'
import {type PetMsg, type PetRsp, petChannel} from '../shared/api.ts'
import {
  type Action,
  HATCH_HOURS,
  isOver,
  OVERFED,
  type PetState,
  tick,
  view,
} from '../shared/pet.ts'
import {Painter} from './draw.ts'
import {fetchAct, fetchPet} from './fetch.ts'

const $ = <T extends HTMLElement>(id: string): T =>
  document.getElementById(id) as T

const app = $<HTMLElement>('app')
const canvas = $<HTMLCanvasElement>('pet')
const mood = $<HTMLParagraphElement>('mood')
const sleepBtn = $<HTMLButtonElement>('sleep')
const nextBtn = $<HTMLButtonElement>('next')
const buttons = [
  ...document.querySelectorAll<HTMLButtonElement>('button[data-action]'),
]
const meterIds = ['fed', 'joy', 'life', 'salt'] as const

/** What the server last told us, and when by our own clock. */
let latest: PetRsp | undefined
let receivedAt = 0
/** The creature's own reply to an action, shown in place of its mood a while. */
let said = ''
let saidUntil = 0
let busy = false

const painter = new Painter(canvas.getContext('2d')!, {
  stage: 0,
  phenotype: 0,
  asleep: false,
  dead: false,
  sick: false,
  hatchProgress: 0,
  ascended: false,
})

/** Server time now, in Unix seconds, carried forward on our clock. */
function now(): number {
  if (!latest) return Date.now() / 1000
  return latest.now + (performance.now() - receivedAt) / 1000
}

function accept(rsp: PetRsp): void {
  latest = rsp
  receivedAt = performance.now()
  app.removeAttribute('aria-busy')
  render()
}

function say(line: string): void {
  said = line
  saidUntil = performance.now() + 6000
  render()
}

// ------------------------------------------------------------------ render

function render(): void {
  if (!latest) return
  const t = now()
  const pet = tick(latest.pet, t)
  const v = view(pet, t)
  const over = isOver(pet)

  $('name').textContent = pet.name
  $('species').textContent =
    v.stage === 0 && !over
      ? `hatches in ${duration((1 - v.hatchProgress) * HATCH_HOURS * 3600)}`
      : v.stage === 0
        ? 'never hatched'
        : v.species
  $('stage').textContent = pet.ascended
    ? 'Elder god, ascended'
    : pet.departedAt
      ? `${v.stageName}, departed`
      : pet.dead
        ? `${v.stageName}, dead`
        : v.stageName
  $('age').textContent = `${v.ageLabel} old · gen ${pet.generation}`

  const look = {
    stage: v.stage,
    phenotype: pet.phenotype,
    asleep: pet.asleep,
    dead: pet.dead || pet.departedAt > 0,
    sick: pet.sick,
    hatchProgress: v.hatchProgress,
    ascended: pet.ascended,
  }
  painter.look = look
  canvas.setAttribute(
    'aria-label',
    v.stage === 0
      ? 'An egg, cracking'
      : `${pet.name}, ${v.species}, ${v.stageName}${pet.asleep ? ', asleep' : ''}${pet.sick ? ', sick' : ''}`,
  )

  const showSaid = said && performance.now() < saidUntil
  mood.className = v.danger ? 'danger' : pet.sick && !over ? 'sick' : ''
  mood.textContent = v.danger
    ? `A meter is empty: ${v.graceMinutes >= 60 ? `${Math.floor(v.graceMinutes / 60)} h` : `${v.graceMinutes} min`} left. Care now.`
    : showSaid
      ? said
      : v.mood

  meterIds.forEach((id, i) => {
    const value = pet.meters[i]!
    $(`bar-${id}`).style.width = `${value}%`
    $(`val-${id}`).textContent = String(Math.round(value))
    $(`bar-${id}`)
      .closest('.meter')!
      .classList.toggle('empty', value <= 0 && !over)
  })

  app.classList.toggle('over', over)
  if (over) renderEnded(pet)
  else renderActions(pet, t)
  renderRecent(pet, t)
  requestAnimationFrame(() => app.classList.add('ready'))
}

function renderActions(pet: PetState, t: number): void {
  const waiting = latest!.loggedIn && latest!.readyAt > t
  for (const b of buttons) b.disabled = busy || waiting

  sleepBtn.dataset.action = pet.asleep ? 'wake' : 'sleep'
  sleepBtn.querySelector('b')!.textContent = pet.asleep ? 'Wake' : 'Sleep'
  sleepBtn.querySelector('small')!.textContent = pet.asleep
    ? 'it rests'
    : '+LIFE'

  const feed = buttons.find(b => b.dataset.action === 'feed')!
  const full = pet.meters[0] > OVERFED
  feed.classList.toggle('warn', full)
  feed.querySelector('small')!.textContent = full ? 'too full' : '+FED'
  feed.title = full ? 'It is full. Feeding it now will make it sick.' : ''
  const ward = buttons.find(b => b.dataset.action === 'ward')!
  ward.querySelector('small')!.textContent = pet.sick ? 'cures at 95' : '+SALT'

  $('turn').textContent = !latest!.loggedIn
    ? 'Log in to take a turn. Anyone can watch.'
    : waiting
      ? `Your next turn in ${duration(latest!.readyAt - t)}.`
      : `Your turn. ${pet.survival ? 'Survival rules: it can die fast.' : 'Gentle rules: it can still die.'} ${ascends(pet, t)}`
}

function renderEnded(pet: PetState): void {
  const tends = `${pet.tends} ${pet.tends === 1 ? 'act' : 'acts'} of care`
  $('ended-text').textContent = pet.ascended
    ? `${pet.name} became an Elder god after ${tends}. It has ascended.`
    : pet.dead
      ? `${pet.name} died after ${tends}.`
      : `${pet.name} went back to the Others after ${tends}.`
  nextBtn.hidden = !latest!.nextUrl
  $('turn').textContent = latest!.nextUrl
    ? ''
    : 'A new egg will appear when a moderator lays one.'
}

const PAST: Record<Action, string> = {
  feed: 'fed',
  play: 'played',
  ward: 'warded',
  sleep: 'put to sleep',
  wake: 'woken',
}

function renderRecent(pet: PetState, t: number): void {
  $('recent').textContent = pet.log.length
    ? `Recently: ${pet.log
        .slice(0, 3)
        .map(e => `${PAST[e.a]} ${duration(t - e.t)} ago`)
        .join(' · ')}`
    : 'Nobody has tended it yet.'
}

function ascends(pet: PetState, t: number): string {
  const v = view(pet, t)
  return v.ascendsIn > 0 ? `Elder god in ${duration(v.ascendsIn)}.` : ''
}

function duration(seconds: number): string {
  const s = Math.max(0, Math.round(seconds))
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.ceil(s / 60)} min`
  if (s < 48 * 3600) return `${Math.floor(s / 3600)} h`
  return `${Math.floor(s / 86400)} d`
}

// ----------------------------------------------------------------- actions

async function onAction(action: Action): Promise<void> {
  if (!latest) return
  if (!latest.loggedIn) {
    showLoginPrompt()
    return
  }
  busy = true
  render()
  const rsp = await fetchAct(action)
  busy = false
  if (!rsp.ok) {
    say(
      rsp.status === 0
        ? 'it cannot hear you. check your connection'
        : rsp.error,
    )
    return
  }
  accept(rsp.value)
  say(rsp.value.said)
}

for (const b of buttons) {
  b.addEventListener('click', () => void onAction(b.dataset.action as Action))
}

nextBtn.addEventListener('click', () => {
  if (latest?.nextUrl) navigateTo(latest.nextUrl)
})

// --------------------------------------------------------------- animation

const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
let phase = 0
let lastFrame = 0
let visible = true
let frame = 0

function resize(): void {
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  const {width, height} = canvas.getBoundingClientRect()
  canvas.width = Math.max(1, Math.round(width * dpr))
  canvas.height = Math.max(1, Math.round(height * dpr))
  canvas.getContext('2d')!.setTransform(dpr, 0, 0, dpr, 0, 0)
  paint()
}

function paint(): void {
  const {width, height} = canvas.getBoundingClientRect()
  painter.phase = phase
  painter.paint(width, height)
}

function loop(at: number): void {
  frame = 0
  if (!visible || document.hidden || reducedMotion.matches) return
  // The phone advanced 0.16 every 130 ms; keep that speed at any frame rate.
  if (lastFrame) phase += (0.16 * Math.min(250, at - lastFrame)) / 130
  lastFrame = at
  paint()
  frame = requestAnimationFrame(loop)
}

function animate(): void {
  if (frame || !visible || document.hidden) return
  if (reducedMotion.matches) {
    paint()
    return
  }
  lastFrame = 0
  frame = requestAnimationFrame(loop)
}

new ResizeObserver(resize).observe(canvas)
new IntersectionObserver(entries => {
  visible = entries.some(e => e.isIntersecting)
  animate()
}).observe(canvas)
document.addEventListener('visibilitychange', () => {
  animate()
  if (!document.hidden) void refresh()
})
reducedMotion.addEventListener('change', animate)

// ----------------------------------------------------------------- startup

async function refresh(): Promise<void> {
  const rsp = await fetchPet()
  if (rsp.ok) accept(rsp.value)
}

async function start(): Promise<void> {
  // A brand new post can be seen a moment before its egg is stored.
  for (let attempt = 0; attempt < 8; attempt++) {
    const rsp = await fetchPet()
    if (rsp.ok) {
      accept(rsp.value)
      break
    }
    $('stage').textContent =
      rsp.status === 404 ? 'the egg is still being laid' : 'it is not answering'
    await new Promise(r => setTimeout(r, 1500))
  }
  animate()
  // Meters, mood and turn timers move on their own between updates.
  setInterval(() => {
    render()
    if (reducedMotion.matches) paint()
  }, 1000)

  if (context.postId) {
    await connectRealtime({
      channel: petChannel(context.postId),
      onMessage: data => {
        const msg = data as PetMsg
        if (!latest) return
        accept({...latest, pet: msg.pet, now: msg.now})
        // Someone else's care, seen live.
        say(msg.said)
        // An ending changes what this post links to; ask once.
        if (isOver(msg.pet)) void refresh()
      },
    })
  }
}

void start()
