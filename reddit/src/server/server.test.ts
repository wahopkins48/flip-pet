import assert from 'node:assert/strict'
import {createServer} from 'node:http'
import type {AddressInfo, Server} from 'node:net'
import {after, before, beforeEach, test} from 'node:test'
import {
  type Context,
  realtime,
  reddit,
  redis,
  runWithContext,
  settings,
} from '@devvit/web/server'
import type {T3, UiResponse} from '@devvit/web/shared'
import {type ActRsp, Endpoint, type PetMsg, type PetRsp} from '../shared/api.ts'
import {ASCEND_SECONDS, type PetState} from '../shared/pet.ts'
import {onReq} from './server.ts'

// --------------------------------------------------------------- fakes

/** Just enough Redis: strings with expiry, and WATCH that really conflicts. */
const store = new Map<string, {value: string; expires: number}>()
const versions = new Map<string, number>()
let clock = 1_760_140_800_000

function live(key: string): string | undefined {
  const entry = store.get(key)
  if (entry?.expires && entry.expires <= clock) store.delete(key)
  return store.get(key)?.value
}

function write(key: string, value: string, expires = 0): void {
  store.set(key, {value, expires})
  versions.set(key, (versions.get(key) ?? 0) + 1)
}

/** Yields so concurrent requests interleave the way real round trips do. */
const tickAway = () => new Promise<void>(resolve => setImmediate(resolve))

const fakeRedis = {
  async get(key: string) {
    await tickAway()
    return live(key)
  },
  async set(
    key: string,
    value: string,
    opts?: {nx?: boolean; expiration?: Date},
  ) {
    await tickAway()
    if (opts?.nx && live(key) !== undefined) return ''
    write(key, value, opts?.expiration?.getTime() ?? 0)
    return 'OK'
  },
  async del(...keys: string[]) {
    for (const key of keys) {
      store.delete(key)
      versions.set(key, (versions.get(key) ?? 0) + 1)
    }
  },
  async incrBy(key: string, n: number) {
    const value = Number(live(key) ?? 0) + n
    write(key, String(value))
    return value
  },
  async watch(...keys: string[]) {
    const seen = keys.map(k => versions.get(k) ?? 0)
    const queued: [string, string][] = []
    return {
      async multi() {},
      async unwatch() {},
      async set(key: string, value: string) {
        queued.push([key, value])
      },
      async exec() {
        await tickAway()
        if (keys.some((k, i) => (versions.get(k) ?? 0) !== seen[i])) {
          throw Error('transaction aborted')
        }
        for (const [k, v] of queued) write(k, v)
        return queued.map(() => 'OK')
      },
    }
  },
}

const sent: {channel: string; msg: PetMsg}[] = []
const posts: {id: T3; title: string; subredditName: string}[] = []
let config: Record<string, unknown> = {}

const saved = {
  redis: {...redis},
  send: realtime.send,
  submit: reddit.submitCustomPost,
  getAll: settings.getAll,
  now: Date.now,
}

// ------------------------------------------------------------- harness

let server: Server
let serverURL: string

before(async () => {
  Object.assign(redis, fakeRedis)
  realtime.send = async (channel, msg) => {
    sent.push({channel, msg: msg as PetMsg})
  }
  reddit.submitCustomPost = (async (opts: {
    title: string
    subredditName: string
  }) => {
    const id: T3 = `t3_${posts.length + 1}`
    posts.push({id, title: opts.title, subredditName: opts.subredditName})
    return {id, url: `https://www.reddit.com/r/test/comments/${id}`}
  }) as unknown as typeof reddit.submitCustomPost
  settings.getAll = (async () => config) as typeof settings.getAll
  Date.now = () => clock

  // Each request carries its own Reddit context, so concurrent calls from
  // different members never borrow each other's identity.
  server = createServer(async (req, rsp) => {
    const as = JSON.parse(String(req.headers['x-test-context'] ?? '{}'))
    await runWithContext(
      {appName: 'elderflip', subredditName: 'test', ...as} as Context,
      () => onReq(req, rsp),
    )
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  serverURL = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})

after(async () => {
  Object.assign(redis, saved.redis)
  realtime.send = saved.send
  reddit.submitCustomPost = saved.submit
  settings.getAll = saved.getAll
  Date.now = saved.now
  await new Promise<void>((resolve, reject) =>
    server.close(err => (err ? reject(err) : resolve())),
  )
})

beforeEach(() => {
  store.clear()
  versions.clear()
  sent.length = 0
  posts.length = 0
  config = {}
  clock = 1_760_140_800_000
})

async function call<T>(
  endpoint: Endpoint,
  as: Partial<Context>,
  body?: object,
): Promise<{status: number; json: T}> {
  const rsp = await fetch(`${serverURL}/${endpoint}`, {
    method: body ? 'POST' : 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Test-Context': JSON.stringify(as),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  return {status: rsp.status, json: (await rsp.json()) as T}
}

async function install(): Promise<T3> {
  await call(Endpoint.OnAppInstall, {}, {type: 'AppInstall'})
  return posts.at(-1)!.id
}

function stored(postId: string): PetState {
  return JSON.parse(live(`pet:${postId}`)!) as PetState
}

/** As if the community kept it fed right up to its last hour. */
function tendedToTheEnd(postId: T3): void {
  const s = stored(postId)
  s.last = s.born + ASCEND_SECONDS - 3600
  write(`pet:${postId}`, JSON.stringify(s))
}

const alice = (postId: T3) => ({postId, userId: 't2_alice'}) as Partial<Context>
const bob = (postId: T3) => ({postId, userId: 't2_bob'}) as Partial<Context>

// --------------------------------------------------------------- tests

test('installing lays the first egg in its own post', async () => {
  const t3 = await install()
  assert.equal(posts.length, 1)
  assert.equal(posts[0]!.subredditName, 'test')
  assert.match(
    posts[0]!.title,
    /^An egg has appeared\. Raise \w+ together\. \(Generation 1\)$/,
  )
  assert.equal(JSON.parse(live('current')!).postId, t3)
  const pet = stored(t3)
  assert.equal(pet.generation, 1)
  assert.equal(pet.survival, false, 'gentle by default')
})

test('anyone can look; only logged-in members can act', async () => {
  const t3 = await install()
  const look = await call<PetRsp>(Endpoint.GetPet, {postId: t3})
  assert.equal(look.status, 200)
  assert.equal(look.json.loggedIn, false)
  assert.equal(look.json.pet.name, stored(t3).name)

  const act = await call<ActRsp>(Endpoint.Act, {postId: t3}, {action: 'feed'})
  assert.equal(act.json.ok, false)
  assert.equal(!act.json.ok && act.json.reason, 'login')
  assert.equal(stored(t3).tends, 0)
})

test('a turn changes the shared creature and tells every viewer', async () => {
  const t3 = await install()
  const rsp = await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'feed'})
  assert.ok(rsp.json.ok)
  assert.equal(rsp.json.said, 'it eats. the room gets quieter')
  assert.equal(stored(t3).meters[0], 100, '70 + 38, clamped')
  assert.equal(sent.length, 1)
  assert.equal(sent[0]!.channel, `pet_${t3}`)
  assert.doesNotMatch(
    JSON.stringify(sent[0]!.msg),
    /alice|t2_/,
    'no user data broadcast',
  )
})

test('each member waits out their own cooldown', async () => {
  config = {cooldownMinutes: 10}
  const t3 = await install()
  assert.ok(
    (await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'ward'})).json.ok,
  )

  const again = await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'ward'})
  assert.equal(!again.json.ok && again.json.reason, 'cooldown')
  assert.equal(again.json.readyAt, Math.floor(clock / 1000) + 600)

  assert.ok(
    (await call<ActRsp>(Endpoint.Act, bob(t3), {action: 'play'})).json.ok,
  )
  clock += 601_000
  assert.ok(
    (await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'feed'})).json.ok,
  )
  assert.equal(stored(t3).tends, 3)
})

test('the cooldown key is the only user data, and it expires', async () => {
  const t3 = await install()
  await call(Endpoint.Act, alice(t3), {action: 'feed'})
  const withUser = [...store.keys()].filter(k => k.includes('t2_'))
  assert.deepEqual(withUser, [`cooldown:${t3}:t2_alice`])
  assert.ok(store.get(withUser[0]!)!.expires > 0)
  assert.doesNotMatch(live(`pet:${t3}`)!, /t2_|alice/)
})

test('a crowd acting at once loses nobody’s care', async () => {
  const t3 = await install()
  const crowd = Array.from({length: 25}, (_, i) => ({
    postId: t3,
    userId: `t2_u${i}`,
  }))
  const results = await Promise.all(
    crowd.map(as =>
      call<ActRsp>(Endpoint.Act, as as Partial<Context>, {action: 'ward'}),
    ),
  )
  assert.ok(results.every(r => r.json.ok))
  assert.equal(stored(t3).tends, 25, 'every concurrent action landed')
})

test('if the creature stays too busy, the turn is given back', async () => {
  const t3 = await install()
  const watch = redis.watch
  redis.watch = (async (...keys: string[]) => {
    const tx = await fakeRedis.watch(...keys)
    return {
      ...tx,
      async exec() {
        throw Error('transaction aborted')
      },
    }
  }) as unknown as typeof redis.watch
  try {
    const rsp = await call<{error: string}>(Endpoint.Act, alice(t3), {
      action: 'feed',
    })
    assert.equal(rsp.status, 503)
  } finally {
    redis.watch = watch
  }
  assert.equal(live(`cooldown:${t3}:t2_alice`), undefined)
  assert.ok(
    (await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'feed'})).json.ok,
  )
})

test('a double tap takes one turn, not two', async () => {
  const t3 = await install()
  const [a, b] = await Promise.all([
    call<ActRsp>(Endpoint.Act, alice(t3), {action: 'play'}),
    call<ActRsp>(Endpoint.Act, alice(t3), {action: 'play'}),
  ])
  assert.equal([a, b].filter(r => r.json.ok).length, 1)
  assert.equal(stored(t3).tends, 1)
})

test('after three days it ascends and the next egg is laid once', async () => {
  const t3 = await install()
  tendedToTheEnd(t3)
  clock += (ASCEND_SECONDS + 60) * 1000
  const [one, two] = await Promise.all([
    call<PetRsp>(Endpoint.GetPet, {postId: t3}),
    call<PetRsp>(Endpoint.GetPet, {postId: t3}),
  ])
  assert.ok(one.json.pet.ascended && two.json.pet.ascended)
  assert.equal(posts.length, 2, 'exactly one new egg')
  const next = JSON.parse(live('current')!)
  assert.equal(next.postId, 't3_2')
  assert.equal(stored('t3_2').generation, 2)

  const later = await call<PetRsp>(Endpoint.GetPet, {postId: t3})
  assert.equal(
    later.json.nextUrl,
    next.url,
    'the old post points to the new egg',
  )
  const act = await call<ActRsp>(Endpoint.Act, alice(t3), {action: 'feed'})
  assert.equal(!act.json.ok && act.json.reason, 'over')
  assert.equal(
    await redis.get(`cooldown:${t3}:t2_alice`),
    undefined,
    'no turn spent on a finished creature',
  )
})

test('the scheduler ends a creature nobody is watching', async () => {
  config = {careMode: ['survival']}
  const t3 = await install()
  assert.equal(stored(t3).survival, true, 'select settings arrive as a list')
  clock += 6 * 3600 * 1000
  await call(Endpoint.OnTend, {}, {name: 'tend'})
  assert.ok(stored(t3).dead, 'starved in survival')
  assert.equal(posts.length, 2, 'next egg laid')
  assert.ok(
    sent.some(s => s.msg.pet.dead),
    'watchers were told',
  )
  await call(Endpoint.OnTend, {}, {name: 'tend'})
  assert.equal(posts.length, 2, 'and only once')
})

test('with auto eggs off, an ending waits for a moderator', async () => {
  config = {autoNewEgg: false}
  const t3 = await install()
  tendedToTheEnd(t3)
  clock += (ASCEND_SECONDS + 1) * 1000
  await call(Endpoint.OnTend, {}, {name: 'tend'})
  assert.ok(stored(t3).ascended)
  assert.equal(posts.length, 1)
  const look = await call<PetRsp>(Endpoint.GetPet, {postId: t3})
  assert.equal(look.json.nextUrl, undefined)
})

test('a moderator replacing a living creature must confirm', async () => {
  const t3 = await install()
  const menu = await call<UiResponse>(
    Endpoint.OnMenuNewEgg,
    {},
    {location: 'subreddit', targetId: 't5_1'},
  )
  assert.equal(menu.json.showForm?.name, 'newEgg')
  assert.equal(posts.length, 1)

  const unticked = await call<UiResponse>(
    Endpoint.OnFormNewEgg,
    {},
    {confirm: false},
  )
  assert.equal(posts.length, 1)
  assert.match(String(unticked.json.showToast), /No egg laid/)

  const done = await call<UiResponse>(
    Endpoint.OnFormNewEgg,
    {},
    {confirm: true},
  )
  assert.equal(posts.length, 2)
  assert.equal(
    done.json.navigateTo,
    'https://www.reddit.com/r/test/comments/t3_2',
  )
  const old = stored(t3)
  assert.ok(
    old.departedAt > 0 && !old.dead && !old.ascended,
    'it departs, not dies',
  )
})

test('when nothing is alive, the menu lays an egg at once', async () => {
  const t3 = await install()
  tendedToTheEnd(t3)
  clock += (ASCEND_SECONDS + 1) * 1000
  config = {autoNewEgg: false}
  await call(Endpoint.OnTend, {}, {name: 'tend'})
  const menu = await call<UiResponse>(
    Endpoint.OnMenuNewEgg,
    {},
    {location: 'subreddit', targetId: 't5_1'},
  )
  assert.equal(menu.json.showForm, undefined)
  assert.equal(posts.length, 2)
  assert.notEqual(JSON.parse(live('current')!).postId, t3)
})

test('renaming picks from the dictionary and tells viewers', async () => {
  const t3 = await install()
  clock += 1000
  const rsp = await call<UiResponse>(
    Endpoint.OnMenuRename,
    {},
    {location: 'post', targetId: t3},
  )
  const name = stored(t3).name
  assert.match(name, /^[A-Z][a-z]+$/)
  assert.match(JSON.stringify(rsp.json.showToast), new RegExp(name))
  assert.equal(sent.at(-1)!.msg.pet.name, name)
})

test('deleting the post deletes its creature', async () => {
  const t3 = await install()
  await call(Endpoint.OnPostDelete, {}, {postId: t3, type: 'PostDelete'})
  assert.equal(live(`pet:${t3}`), undefined)
  assert.equal(live('current'), undefined)
  const look = await call<PetRsp>(Endpoint.GetPet, {postId: t3})
  assert.equal(look.status, 404)
})

test('cooldown setting is validated for moderators', async () => {
  const check = (value: unknown) =>
    call<{success: boolean}>(
      Endpoint.OnValidateCooldown,
      {},
      {value, isEditing: true},
    )
  assert.equal((await check(10)).json.success, true)
  assert.equal((await check(0)).json.success, false)
  assert.equal((await check(241)).json.success, false)
  assert.equal((await check(2.5)).json.success, false)
})

test('unknown routes and actions are refused', async () => {
  assert.equal((await fetch(`${serverURL}/nope`)).status, 404)
  const t3 = await install()
  const bad = await call(Endpoint.Act, alice(t3), {action: 'pet'})
  assert.equal(bad.status, 400)
})
