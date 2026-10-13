import type { Env } from '../env.ts'

export interface NoticeRecord {
  hash: string
  title: string
  message: string
  published_at: string
  first_seen_at: string
  etag: string | null
}

export interface NoticeIndexEntry {
  hash: string
  title: string
  published_at: string
  first_seen_at: string
}

const CURRENT_KEY = 'notice:current'
const INDEX_KEY = 'notice:index'
const VERSION_PREFIX = 'notice:version:'
const FAILURE_KEY = 'notice:failure'
export const NOTICE_INDEX_LIMIT = 500
const VERSION_TTL = 400 * 86400

export async function readNoticeCurrent(env: Env): Promise<NoticeRecord | null> {
  const raw = await env.SKYDAILY_KV.get(CURRENT_KEY)
  return raw === null ? null : JSON.parse(raw) as NoticeRecord
}

export async function readNoticeVersion(env: Env, hash: string): Promise<NoticeRecord | null> {
  const raw = await env.SKYDAILY_KV.get(VERSION_PREFIX + hash)
  return raw === null ? null : JSON.parse(raw) as NoticeRecord
}

export async function readNoticeIndex(env: Env): Promise<NoticeIndexEntry[]> {
  const raw = await env.SKYDAILY_KV.get(INDEX_KEY)
  return raw === null ? [] : JSON.parse(raw) as NoticeIndexEntry[]
}

export async function writeNoticeRecord(env: Env, record: NoticeRecord): Promise<void> {
  const index = await readNoticeIndex(env)
  await env.SKYDAILY_KV.put(CURRENT_KEY, JSON.stringify(record))
  await env.SKYDAILY_KV.put(VERSION_PREFIX + record.hash, JSON.stringify(record), { expirationTtl: VERSION_TTL })
  if (!index.some(entry => entry.hash === record.hash)) {
    index.push({ hash: record.hash, title: record.title, published_at: record.published_at, first_seen_at: record.first_seen_at })
    index.sort((a, b) => a.first_seen_at.localeCompare(b.first_seen_at))
    await env.SKYDAILY_KV.put(INDEX_KEY, JSON.stringify(index.slice(-NOTICE_INDEX_LIMIT)))
  }
}

export async function readNoticeHistory(env: Env, limit = 200): Promise<NoticeRecord[]> {
  const index = await readNoticeIndex(env)
  const recent = index.slice(-limit).reverse()
  const versions = await Promise.all(recent.map(entry => readNoticeVersion(env, entry.hash)))
  return versions.filter((record): record is NoticeRecord => record !== null)
}

export async function markNoticeFailure(env: Env, at: string): Promise<void> {
  await env.SKYDAILY_KV.put(FAILURE_KEY, at, { expirationTtl: 7 * 86400 })
}

export async function clearNoticeFailure(env: Env): Promise<void> {
  await env.SKYDAILY_KV.delete(FAILURE_KEY)
}

export async function readNoticeFailure(env: Env): Promise<string | null> {
  return env.SKYDAILY_KV.get(FAILURE_KEY)
}
