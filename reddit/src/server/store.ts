/**
 * Redis keys, one installation (subreddit) each:
 *
 *   pet:{t3}          the creature living in that post, as JSON
 *   current           {postId, url} of the newest egg
 *   generation        how many eggs this subreddit has laid
 *   over:{t3}         set once, so a creature's ending is handled once
 *   cooldown:{t3}:{t2}  a short-lived lock per Redditor; expires on its own
 *
 * The cooldown key is the only place a user ID is stored, and it disappears
 * within the cooldown (four hours at most). Nothing else records who did what.
 */
import {redis} from '@devvit/web/server'
import {type PetState, tick} from '../shared/pet.ts'

const TX_ATTEMPTS = 24
/** Spreads retries out so a burst of taps on one post settles instead of colliding again. */
const BACKOFF_MS = 15

export type Current = {postId: string; url: string}

function petKey(postId: string): string {
  return `pet:${postId}`
}

function cooldownKey(postId: string, userId: string): string {
  return `cooldown:${postId}:${userId}`
}

export async function loadPet(postId: string): Promise<PetState | undefined> {
  const raw = await redis.get(petKey(postId))
  return raw ? (JSON.parse(raw) as PetState) : undefined
}

export async function savePet(postId: string, pet: PetState): Promise<void> {
  await redis.set(petKey(postId), JSON.stringify(pet))
}

export async function deletePet(postId: string): Promise<void> {
  await redis.del(petKey(postId), `over:${postId}`)
  const current = await loadCurrent()
  if (current?.postId === postId) await redis.del('current')
}

/**
 * Reads, changes, and writes one creature atomically. Many people act on the
 * same post at once; a lost update would quietly undo someone's care.
 * `change` must be pure because a conflict reruns it.
 */
export async function updatePet<T>(
  postId: string,
  change: (pet: PetState) => {pet: PetState; result: T},
): Promise<{before: PetState; pet: PetState; result: T} | undefined> {
  const key = petKey(postId)
  for (let attempt = 0; attempt < TX_ATTEMPTS; attempt++) {
    if (attempt) await sleep(Math.random() * BACKOFF_MS * Math.min(attempt, 8))
    const tx = await redis.watch(key)
    // A transaction client only queues commands; read outside it. The watch
    // still aborts the write if anyone changes the key after this point.
    const raw = await redis.get(key)
    if (!raw) {
      await tx.unwatch()
      return
    }
    const before = JSON.parse(raw) as PetState
    const {pet, result} = change(before)
    const json = JSON.stringify(pet)
    await tx.multi()
    await tx.set(key, json)
    if (await committed(tx.exec(), key, json)) return {before, pet, result}
  }
  throw new BusyError(`pet ${postId} is too busy; try again`)
}

export class BusyError extends Error {}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * A watched key that changed aborts the transaction, which the client reports
 * as an error or an empty reply depending on the path. When in doubt, the
 * stored value says whether our write landed.
 */
async function committed(
  exec: Promise<unknown[] | null>,
  key: string,
  json: string,
): Promise<boolean> {
  let replies: unknown[] | null
  try {
    replies = await exec
  } catch {
    return false
  }
  if (replies && replies.length > 0) return true
  return (await redis.get(key)) === json
}

/** Folds elapsed time into storage. */
export async function settlePet(
  postId: string,
  now: number,
): Promise<PetState | undefined> {
  const done = await updatePet(postId, pet => ({
    pet: tick(pet, now),
    result: 0,
  }))
  return done?.pet
}

/** True for exactly one caller per post, however many race to end it. */
export async function claimEnding(postId: string): Promise<boolean> {
  return Boolean(await redis.set(`over:${postId}`, '1', {nx: true}))
}

export async function loadCurrent(): Promise<Current | undefined> {
  const raw = await redis.get('current')
  return raw ? (JSON.parse(raw) as Current) : undefined
}

export async function saveCurrent(current: Current): Promise<void> {
  await redis.set('current', JSON.stringify(current))
}

export async function nextGeneration(): Promise<number> {
  return redis.incrBy('generation', 1)
}

/**
 * Takes this Redditor's turn on this post. Returns 0 when the turn is theirs,
 * or the Unix second it frees up. Set-if-absent means a double tap cannot act
 * twice.
 */
export async function claimTurn(
  postId: string,
  userId: string,
  now: number,
  cooldownSeconds: number,
): Promise<number> {
  const key = cooldownKey(postId, userId)
  const until = now + cooldownSeconds
  const claimed = await redis.set(key, String(until), {
    nx: true,
    expiration: new Date(until * 1000),
  })
  // A refused set-if-absent answers with an empty string, not null.
  if (claimed) return 0
  return Number((await redis.get(key)) ?? now)
}

export async function releaseTurn(
  postId: string,
  userId: string,
): Promise<void> {
  await redis.del(cooldownKey(postId, userId))
}

/** Unix second this Redditor may act again, or 0. */
export async function turnReadyAt(
  postId: string,
  userId: string,
  now: number,
): Promise<number> {
  const until = Number((await redis.get(cooldownKey(postId, userId))) ?? 0)
  return until > now ? until : 0
}
