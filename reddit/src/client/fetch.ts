import {
  type ActReq,
  type ActRsp,
  Endpoint,
  type ErrorRsp,
  type PetRsp,
} from '../shared/api.ts'
import type {Action} from '../shared/pet.ts'

export type Fetched<T> =
  | {ok: true; value: T}
  | {ok: false; status: number; error: string}

async function call<T>(
  endpoint: Endpoint,
  init?: RequestInit,
): Promise<Fetched<T>> {
  let rsp: Response
  try {
    rsp = await fetch(endpoint, {
      ...init,
      headers: {Accept: 'application/json', ...init?.headers},
    })
  } catch (err) {
    return {
      ok: false,
      status: 0,
      error: err instanceof Error ? err.message : `${err}`,
    }
  }
  const body = (await rsp.json().catch(() => undefined)) as
    | (T & object)
    | ErrorRsp
    | undefined
  if (!rsp.ok || !body) {
    const error = body && 'error' in body ? String(body.error) : rsp.statusText
    return {ok: false, status: rsp.status, error}
  }
  return {ok: true, value: body as T}
}

export function fetchPet(): Promise<Fetched<PetRsp>> {
  return call<PetRsp>(Endpoint.GetPet)
}

export function fetchAct(action: Action): Promise<Fetched<ActRsp>> {
  const req: ActReq = {action}
  return call<ActRsp>(Endpoint.Act, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(req),
  })
}
