import type { Env } from '../env.ts'

export interface NoticeConditional {
  etag?: string | null
  lastModified?: string | null
}

export interface NoticeFetch {
  status: 'unchanged' | 'changed'
  body?: unknown
  etag: string | null
  lastModified: string | null
}

export async function fetchNotice(env: Env, conditional: NoticeConditional | null, signal?: AbortSignal): Promise<NoticeFetch> {
  if (!env.NOTICE_URL) throw new Error('notice_not_configured')
  const target = new URL(env.NOTICE_URL)
  const headers: Record<string, string> = { accept: 'application/json' }
  if (conditional?.etag) headers['If-None-Match'] = conditional.etag
  else if (conditional?.lastModified && Number.isFinite(Date.parse(conditional.lastModified))) {
    headers['If-Modified-Since'] = new Date(conditional.lastModified).toUTCString()
  }
  const response = await fetch(target, {
    headers,
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20_000)]) : AbortSignal.timeout(20_000),
  })
  if (response.url && new URL(response.url).origin !== target.origin) throw new Error('notice_origin_mismatch')
  if (response.status === 304) return { status: 'unchanged', etag: conditional?.etag ?? null, lastModified: conditional?.lastModified ?? null }
  if (!response.ok) throw new Error(`notice_http_${response.status}`)
  return {
    status: 'changed',
    body: await response.json(),
    etag: response.headers.get('etag'),
    lastModified: response.headers.get('last-modified'),
  }
}
