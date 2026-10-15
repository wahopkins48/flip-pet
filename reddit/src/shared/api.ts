import type {Action, PetState} from './pet.ts'

/** Generic error detail for all responses. */
export type ErrorRsp = {error: string; status: number}

/** Everything a post needs to draw its creature. */
export type PetRsp = {
  pet: PetState
  /** Server clock, Unix seconds, so the client can keep the meters moving. */
  now: number
  /** Unix seconds when this viewer may act again; 0 if now. */
  readyAt: number
  /** The viewer is logged in and can act. */
  loggedIn: boolean
  /** Set when this post's creature is over and a newer egg exists. */
  nextUrl: string | undefined
}

export type ActReq = {action: Action}

export type ActRsp =
  | ({ok: true; said: string} & PetRsp)
  | ({
      ok: false
      reason: 'login' | 'cooldown' | 'over'
      said: string
    } & PetRsp)

/** Pushed to every viewer of a post after each change. Holds no user data. */
export type PetMsg = {pet: PetState; now: number; said: string}

/** Realtime channel names may not contain a colon. */
export function petChannel(postId: string): string {
  return `pet_${postId}`
}

export type Endpoint = (typeof Endpoint)[keyof typeof Endpoint]
export const Endpoint = {
  GetPet: 'api/pet',
  Act: 'api/act',
  OnAppInstall: 'internal/on/app/install',
  OnPostDelete: 'internal/on/post/delete',
  OnMenuNewEgg: 'internal/menu/new-egg',
  OnMenuRename: 'internal/menu/rename',
  OnFormNewEgg: 'internal/form/new-egg',
  OnTend: 'internal/scheduler/tend',
  OnValidateCooldown: 'internal/settings/validate-cooldown',
} as const

export const EndpointMethod = {
  [Endpoint.GetPet]: 'GET',
  [Endpoint.Act]: 'POST',
  [Endpoint.OnAppInstall]: 'POST',
  [Endpoint.OnPostDelete]: 'POST',
  [Endpoint.OnMenuNewEgg]: 'POST',
  [Endpoint.OnMenuRename]: 'POST',
  [Endpoint.OnFormNewEgg]: 'POST',
  [Endpoint.OnTend]: 'POST',
  [Endpoint.OnValidateCooldown]: 'POST',
} as const satisfies {[endpoint: string]: 'GET' | 'POST'}
