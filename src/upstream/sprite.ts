import type { Env } from '../env.ts'

export interface KnowledgeResult {
  title: string | null
  answer: string
}

export class UpstreamError extends Error {
  readonly code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

interface KnowledgeResponse {
  code?: number
  message?: string
  data?: {
    knowledge?: { title?: string }
    answer?: string
  }
}

export async function queryKnowledge(
  env: Env,
  token: string,
  question: string,
  method = 'link',
  signal?: AbortSignal,
): Promise<KnowledgeResult> {
  const resp = await fetch(env.NETEASE_TASK_API, {
    method: 'POST',
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20_000)]) : AbortSignal.timeout(20_000),
    headers: {
      'content-type': 'application/json',
      accept: 'application/json, text/plain, */*',
      origin: env.NETEASE_TASK_ORIGIN,
      referer: env.NETEASE_TASK_REFERER,
      'token-type': 'gmsdk',
      token,
      'user-agent': env.NETEASE_USER_AGENT,
    },
    body: JSON.stringify({ ismanual: 0, loginFrom: 'sprite', method, question }),
  })
  if (!resp.ok) throw new UpstreamError(resp.status, `sprite http ${resp.status}`)

  const data = (await resp.json()) as KnowledgeResponse
  if (data.code !== 200 || !data.data?.answer) {
    throw new UpstreamError(data.code ?? 0, data.message ?? 'sprite empty answer')
  }
  return { title: data.data.knowledge?.title ?? null, answer: data.data.answer }
}
