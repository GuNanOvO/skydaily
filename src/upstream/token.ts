import type { Env } from '../env.ts'

interface InnerResult {
  code?: number
  message?: string
  token?: string
}

export async function fetchToken(env: Env, signal?: AbortSignal): Promise<string> {
  const resp = await fetch(env.NETEASE_TOKEN_API, {
    method: 'POST',
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20_000)]) : AbortSignal.timeout(20_000),
    headers: {
      'content-type': 'application/json',
      'user-agent': env.NETEASE_USER_AGENT,
    },
    body: JSON.stringify({
      cmd: 'kefu_get_token',
      uid: env.SKY_UID,
      game_uid: env.SKY_GAME_UID,
      os: 'android',
      game_server: Number(env.SKY_GAME_SERVER),
      login_from: 1,
      map: 'CandleSpace',
      return_buff: 'false',
    }),
  })
  if (!resp.ok) throw new Error(`token http ${resp.status}`)

  const outer = (await resp.json()) as { status?: string; result?: string }
  if (outer.status !== 'ok' || !outer.result) throw new Error(`token status ${outer.status}`)

  const inner = JSON.parse(outer.result) as InnerResult
  if (inner.code !== 200 || !inner.token) {
    throw new Error(`token code ${inner.code} ${inner.message ?? ''}`.trim())
  }
  return inner.token
}
