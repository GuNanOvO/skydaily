import type { Env } from '../env.ts'

const DAILY_PREFIX = 'daily:'
const META_KEY = 'meta:lastRefresh'

export async function readDaily(env: Env, date: string): Promise<unknown | null> {
  const raw = await env.SKYDAILY_KV.get(DAILY_PREFIX + date)
  return raw === null ? null : JSON.parse(raw)
}

export async function writeDaily(env: Env, date: string, data: unknown): Promise<void> {
  await env.SKYDAILY_KV.put(DAILY_PREFIX + date, JSON.stringify(data), { expirationTtl: 31 * 86400 })
}

export async function readRefreshMeta(env: Env): Promise<unknown | null> {
  const raw = await env.SKYDAILY_KV.get(META_KEY)
  return raw === null ? null : JSON.parse(raw)
}

export async function writeRefreshMeta(env: Env, meta: unknown): Promise<void> {
  await env.SKYDAILY_KV.put(META_KEY, JSON.stringify(meta))
}
