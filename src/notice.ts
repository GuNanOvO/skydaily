import type { Env } from './env.ts'
import { digest } from './public-data.ts'
import { parseNotice, type NoticePayload } from './parser/notice.ts'
import { fetchNotice } from './upstream/notice.ts'
import {
  clearNoticeFailure,
  markNoticeFailure,
  readNoticeCurrent,
  writeNoticeRecord,
  type NoticeRecord,
} from './store/notice.ts'

export interface NoticePollResult {
  status: 'unchanged' | 'updated' | 'failed'
  record?: NoticeRecord
}

export async function noticeHash(payload: NoticePayload): Promise<string> {
  return digest(`${payload.title.trim()}\n${payload.message.trim()}`)
}

export async function pollNotice(env: Env, options: { signal?: AbortSignal } = {}): Promise<NoticePollResult> {
  const current = await readNoticeCurrent(env)
  let response
  try {
    response = await fetchNotice(env, current ? { etag: current.etag, lastModified: current.published_at } : null, options.signal)
  } catch {
    console.error('notice poll failed')
    await markNoticeFailure(env, new Date().toISOString())
    return { status: 'failed' }
  }
  if (response.status === 'unchanged') {
    await clearNoticeFailure(env)
    return { status: 'unchanged' }
  }
  const payload = parseNotice(response.body)
  if (!payload) {
    console.error('notice payload rejected')
    await markNoticeFailure(env, new Date().toISOString())
    return { status: 'failed' }
  }
  const hash = await noticeHash(payload)
  if (current?.hash === hash) {
    await clearNoticeFailure(env)
    if (current.etag !== response.etag) await writeNoticeRecord(env, { ...current, etag: response.etag })
    return { status: 'unchanged' }
  }
  const seenAt = new Date().toISOString()
  const record: NoticeRecord = {
    hash,
    ...payload,
    published_at: response.lastModified && Number.isFinite(Date.parse(response.lastModified))
      ? new Date(response.lastModified).toISOString()
      : seenAt,
    first_seen_at: seenAt,
    etag: response.etag,
  }
  await writeNoticeRecord(env, record)
  await clearNoticeFailure(env)
  return { status: 'updated', record }
}
