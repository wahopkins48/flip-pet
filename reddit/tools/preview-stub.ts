// Stands in for @devvit/web/client and the app server so the post can be
// looked at in a plain browser: `npm run preview`, then open the printed URL.
// Nothing here ships; the Devvit build never imports it.
// Query string: ?stage=0..5&species=0..5&asleep&sick&dead&ascended&full&out
import {freshPet, type PetState} from '../src/shared/pet.ts'

const q = new URLSearchParams(location.search)
const now = Math.floor(Date.now() / 1000)
const stageAge = [0.5, 2, 6, 20, 40, 72.1][Number(q.get('stage') ?? 3)] ?? 20
const pet: PetState = {
  ...freshPet(now - stageAge * 3600, 4, q.has('survival')),
  name: 'Xothun',
  phenotype: Number(q.get('species') ?? 0),
  asleep: q.has('asleep'),
  sick: q.has('sick'),
  meters: q.has('full') ? [92, 64, 55, 31] : [58, 71, 40, 77],
  tends: 312,
  log: [
    {t: now - 120, a: 'feed', said: ''},
    {t: now - 540, a: 'ward', said: ''},
    {t: now - 1300, a: 'play', said: ''},
  ],
}
pet.last = now
if (q.has('dead'))
  Object.assign(pet, {
    dead: true,
    deadAt: now,
    survival: true,
    cause: 'starved. It forgot what mouths were for.',
  })

export const context = {postId: 't3_preview'}
export function showLoginPrompt(): void {}
export function navigateTo(): void {}
export async function connectRealtime(): Promise<void> {}

const real = window.fetch
window.fetch = async (input, init) => {
  const url = String(input)
  if (!url.startsWith('api/')) return real(input, init)
  const body = {
    pet,
    now,
    readyAt: q.has('wait') ? now + 420 : 0,
    loggedIn: !q.has('out'),
    nextUrl: pet.ascended || pet.dead ? 'https://example.invalid' : undefined,
    ok: true,
    said: 'it eats. the room gets quieter',
  }
  return new Response(JSON.stringify(body), {
    headers: {'Content-Type': 'application/json'},
  })
}
