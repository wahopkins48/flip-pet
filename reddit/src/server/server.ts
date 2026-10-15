import {once} from 'node:events'
import type {IncomingMessage, ServerResponse} from 'node:http'
import {context, realtime, reddit, settings} from '@devvit/web/server'
import type {
  MenuItemRequest,
  OnPostDeleteRequest,
  PartialJsonValue,
  SettingsValidationRequest,
  SettingsValidationResponse,
  TriggerResponse,
  UiResponse,
} from '@devvit/web/shared'
import {
  type ActReq,
  type ActRsp,
  Endpoint,
  EndpointMethod,
  type ErrorRsp,
  type PetMsg,
  type PetRsp,
  petChannel,
} from '../shared/api.ts'
import {
  ACTIONS,
  act,
  depart,
  freshPet,
  isOver,
  type PetState,
  randomName,
  STAGE_NAMES,
  tick,
} from '../shared/pet.ts'
import {
  BusyError,
  claimEnding,
  claimTurn,
  deletePet,
  loadCurrent,
  loadPet,
  nextGeneration,
  releaseTurn,
  saveCurrent,
  savePet,
  settlePet,
  turnReadyAt,
  updatePet,
} from './store.ts'

type AnyRsp =
  | PetRsp
  | ActRsp
  | UiResponse
  | TriggerResponse
  | SettingsValidationResponse
  | ErrorRsp

export async function onReq(
  reqMsg: IncomingMessage,
  rspMsg: ServerResponse,
): Promise<void> {
  try {
    await route(reqMsg, rspMsg)
  } catch (err) {
    const msg = `server error; ${err instanceof Error ? err.stack : err}`
    console.error(msg)
    writeJson<ErrorRsp>(
      500,
      {error: 'something went wrong', status: 500},
      rspMsg,
    )
  }
}

async function route(
  reqMsg: IncomingMessage,
  rspMsg: ServerResponse,
): Promise<void> {
  const endpoint = reqMsg.url?.slice(1).split('?')[0] as Endpoint
  const method = EndpointMethod[endpoint]

  let rsp: AnyRsp
  if (method !== reqMsg.method) {
    rsp = {error: 'not found', status: 404}
  } else {
    switch (endpoint) {
      case Endpoint.GetPet:
        rsp = await routeGetPet()
        break
      case Endpoint.Act:
        rsp = await routeAct(await readJson<ActReq>(reqMsg))
        break
      case Endpoint.OnAppInstall:
        await layEgg()
        rsp = {}
        break
      case Endpoint.OnPostDelete:
        rsp = await routePostDelete(await readJson<OnPostDeleteRequest>(reqMsg))
        break
      case Endpoint.OnMenuNewEgg:
        rsp = await routeMenuNewEgg()
        break
      case Endpoint.OnFormNewEgg:
        rsp = await routeFormNewEgg(await readJson<{confirm?: boolean}>(reqMsg))
        break
      case Endpoint.OnMenuRename:
        rsp = await routeMenuRename(await readJson<MenuItemRequest>(reqMsg))
        break
      case Endpoint.OnTend:
        rsp = await routeTend()
        break
      case Endpoint.OnValidateCooldown:
        rsp = validateCooldown(
          await readJson<SettingsValidationRequest<number>>(reqMsg),
        )
        break
      default:
        endpoint satisfies never
        rsp = {error: 'not found', status: 404}
        break
    }
  }

  writeJson<PartialJsonValue>(
    'status' in rsp && typeof rsp.status === 'number' ? rsp.status : 200,
    rsp as PartialJsonValue,
    rspMsg,
  )
}

// ------------------------------------------------------------------ settings

type Config = {survival: boolean; cooldownSeconds: number; autoNewEgg: boolean}

async function config(): Promise<Config> {
  const all = await settings.getAll<{
    careMode?: string | string[]
    cooldownMinutes?: number
    autoNewEgg?: boolean
  }>()
  const mode = [all.careMode ?? 'gentle'].flat()[0]
  const minutes = Number(all.cooldownMinutes ?? 10)
  return {
    survival: mode === 'survival',
    cooldownSeconds: Math.round(
      Math.min(240, Math.max(1, Number.isFinite(minutes) ? minutes : 10)) * 60,
    ),
    autoNewEgg: all.autoNewEgg ?? true,
  }
}

function validateCooldown(
  req: SettingsValidationRequest<number>,
): SettingsValidationResponse {
  const minutes = Number(req.value)
  if (Number.isInteger(minutes) && minutes >= 1 && minutes <= 240)
    return {success: true}
  return {success: false, error: 'Use a whole number of minutes from 1 to 240.'}
}

// --------------------------------------------------------------------- pets

function nowSeconds(): number {
  return Math.floor(Date.now() / 1000)
}

function postId(): string {
  const t3 = context.postId
  if (!t3) throw Error('no post')
  return t3
}

async function routeGetPet(): Promise<PetRsp | ErrorRsp> {
  const t3 = postId()
  const now = nowSeconds()
  const stored = await loadPet(t3)
  if (!stored) return {error: 'the egg is still being laid', status: 404}
  let pet = tick(stored, now)
  // Whoever first looks after it has ended does the ending, so it does not
  // wait for the next scheduled pass.
  if (!isOver(stored) && isOver(pet)) pet = (await finish(t3, now)) ?? pet
  return petRsp(t3, pet, now)
}

async function petRsp(t3: string, pet: PetState, now: number): Promise<PetRsp> {
  const userId = context.userId
  const current = isOver(pet) ? await loadCurrent() : undefined
  return {
    pet,
    now,
    readyAt: userId ? await turnReadyAt(t3, userId, now) : 0,
    loggedIn: Boolean(userId),
    nextUrl: current && current.postId !== t3 ? current.url : undefined,
  }
}

async function routeAct(req: ActReq): Promise<ActRsp | ErrorRsp> {
  if (!ACTIONS.includes(req.action))
    return {error: 'unknown action', status: 400}
  const t3 = postId()
  const now = nowSeconds()
  const userId = context.userId

  const stored = await loadPet(t3)
  if (!stored) return {error: 'the egg is still being laid', status: 404}

  if (!userId) {
    const pet = tick(stored, now)
    return {
      ok: false,
      reason: 'login',
      said: 'log in to tend it',
      ...(await petRsp(t3, pet, now)),
    }
  }

  const {cooldownSeconds} = await config()
  const readyAt = await claimTurn(t3, userId, now, cooldownSeconds)
  if (readyAt) {
    const pet = tick(stored, now)
    return {
      ok: false,
      reason: 'cooldown',
      said: 'it is still digesting your last attention',
      ...(await petRsp(t3, pet, now)),
    }
  }

  let done: Awaited<ReturnType<typeof updatePet<string>>>
  try {
    done = await updatePet(t3, pet => {
      const after = act(pet, req.action, now)
      // Nothing changes for a creature that is already over; keep its state.
      return isOver(after.state)
        ? {pet: tick(pet, now), result: after.said}
        : {pet: after.state, result: after.said}
    })
  } catch (err) {
    // The turn was never taken; give it back.
    await releaseTurn(t3, userId)
    if (err instanceof BusyError)
      return {error: 'so many hands at once. try again', status: 503}
    throw err
  }
  if (!done) {
    await releaseTurn(t3, userId)
    return {error: 'the egg is still being laid', status: 404}
  }

  if (isOver(done.pet)) {
    // Ended before this action landed; the turn was never spent.
    await releaseTurn(t3, userId)
    if (!isOver(done.before)) await finish(t3, now)
    return {
      ok: false,
      reason: 'over',
      said: done.result,
      ...(await petRsp(t3, done.pet, now)),
    }
  }

  await broadcast(t3, done.pet, now, done.result)
  return {ok: true, said: done.result, ...(await petRsp(t3, done.pet, now))}
}

async function broadcast(
  t3: string,
  pet: PetState,
  now: number,
  said: string,
): Promise<void> {
  try {
    await realtime.send(petChannel(t3), {pet, now, said} satisfies PetMsg)
  } catch (err) {
    // Viewers still catch up on their next load; never fail an action on this.
    console.error(`realtime send failed; ${err}`)
  }
}

/**
 * The creature in this post has died or ascended. Tells everyone watching,
 * and if it was the subreddit's newest, lays the next egg. Runs once per post.
 */
async function finish(t3: string, now: number): Promise<PetState | undefined> {
  const pet = await settlePet(t3, now)
  if (!pet || !isOver(pet)) return pet
  if (!(await claimEnding(t3))) return pet
  await broadcast(
    t3,
    pet,
    now,
    pet.dead
      ? 'it is gone'
      : pet.ascended
        ? 'it has ascended'
        : 'it went back to the Others',
  )
  const current = await loadCurrent()
  if (current?.postId === t3 && (await config()).autoNewEgg) await layEgg()
  return pet
}

/** Lays the next egg in its own post and makes it the subreddit's current one. */
async function layEgg(): Promise<{url: string; postId: string}> {
  const {survival} = await config()
  const now = nowSeconds()
  const generation = await nextGeneration()
  const pet = freshPet(now, generation, survival)
  const post = await reddit.submitCustomPost({
    subredditName: context.subredditName,
    title: `An egg has appeared. Raise ${pet.name} together. (Generation ${generation})`,
    entry: 'default',
    textFallback: {
      text:
        `${pet.name} is an ${STAGE_NAMES[0].toLowerCase()}. It hatches in an hour and ` +
        'becomes an Elder god in three days if this community keeps it fed, ' +
        'happy, rested and warded. Open this post in the Reddit app or on ' +
        'new Reddit to care for it.',
    },
  })
  await savePet(post.id, pet)
  await saveCurrent({postId: post.id, url: post.url})
  return {url: post.url, postId: post.id}
}

// ------------------------------------------------------------- moderation

async function routeMenuNewEgg(): Promise<UiResponse> {
  const current = await loadCurrent()
  const pet = current ? await loadPet(current.postId) : undefined
  if (pet && !isOver(tick(pet, nowSeconds()))) {
    return {
      showForm: {
        name: 'newEgg',
        form: {
          title: 'Lay a new egg?',
          description:
            `${pet.name} is still alive. A new egg becomes the community's ` +
            'creature; the old one stays in its post but nobody can tend it.',
          acceptLabel: 'Lay the egg',
          cancelLabel: 'Never mind',
          fields: [
            {
              type: 'boolean',
              name: 'confirm',
              label: `Let ${pet.name} go back to the Others`,
              defaultValue: false,
            },
          ],
        },
      },
    }
  }
  return laidRsp(await layEgg())
}

async function routeFormNewEgg(values: {
  confirm?: boolean
}): Promise<UiResponse> {
  if (!values.confirm)
    return {showToast: 'No egg laid. Tick the box to confirm.'}
  const current = await loadCurrent()
  if (current) await retire(current.postId)
  return laidRsp(await layEgg())
}

/** A creature replaced by a moderator leaves rather than dies. */
async function retire(t3: string): Promise<void> {
  const now = nowSeconds()
  const done = await updatePet(t3, pet => ({
    pet: depart(pet, now),
    result: !isOver(tick(pet, now)),
  }))
  if (done?.result) {
    await claimEnding(t3)
    await broadcast(t3, done.pet, now, 'it went back to the Others')
  }
}

function laidRsp(laid: {url: string}): UiResponse {
  return {
    showToast: {text: 'A new egg has appeared.', appearance: 'success'},
    navigateTo: laid.url,
  }
}

async function routeMenuRename(req: MenuItemRequest): Promise<UiResponse> {
  const t3 = req.targetId
  const now = nowSeconds()
  const name = randomName(now)
  const done = await updatePet(t3, pet => ({
    pet: {...tick(pet, now), name},
    result: 0,
  }))
  if (!done) return {showToast: 'This post has no Elderflip creature.'}
  await broadcast(t3, done.pet, now, `it will answer to ${name}`)
  return {
    showToast: {text: `It will answer to ${name}.`, appearance: 'success'},
  }
}

async function routePostDelete(
  req: OnPostDeleteRequest,
): Promise<TriggerResponse> {
  if (req.postId) await deletePet(req.postId)
  return {}
}

/** Every few minutes: notice a death or ascension even if nobody is looking. */
async function routeTend(): Promise<TriggerResponse> {
  const current = await loadCurrent()
  if (!current) return {}
  const now = nowSeconds()
  const pet = await settlePet(current.postId, now)
  // Retries an ending interrupted part way; finish() acts only once per post.
  if (pet && isOver(pet)) await finish(current.postId, now)
  return {}
}

// --------------------------------------------------------------------- http

async function readJson<T>(reqMsg: IncomingMessage): Promise<T> {
  const chunks: Uint8Array[] = []
  reqMsg.on('data', chunk => chunks.push(chunk))
  await once(reqMsg, 'end')
  const body = `${Buffer.concat(chunks)}`
  return (body ? JSON.parse(body) : {}) as T
}

function writeJson<T extends PartialJsonValue>(
  status: number,
  json: Readonly<T>,
  rsp: ServerResponse,
): void {
  const body = JSON.stringify(json)
  const len = Buffer.byteLength(body)
  rsp.writeHead(status, {
    'Content-Length': len,
    'Content-Type': 'application/json',
  })
  rsp.end(body)
}
