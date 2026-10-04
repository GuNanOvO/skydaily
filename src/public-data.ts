import type { Env } from './env.ts'
import type { Envelope, TaskDetail } from './schema/types.ts'
import { normalizeSeasonGuideLink, seasonGuidePolicy } from './link-policy.mjs'

export async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, '0')).join('')
}

export function isSeasonGuideLink(value: string): boolean {
  return normalizeSeasonGuideLink(value) !== null
}

export function cleanText(value: string): string {
  return value.replace(/https?:\/\/[^\s<>"'）)]+/gi, '').trim()
}

function isSourceMedia(value: string): boolean {
  const url = new URL(value)
  return url.protocol === 'https:' && url.hostname === 'ok.166.net' && !url.port && !url.username && !url.password
}

function mediaUrls(envelope: Envelope): string[] {
  const urls = new Set<string>()
  const groups = [...envelope.data.taskDetails, ...envelope.data.candles, envelope.data.weather, envelope.data.calendar]
  for (const detail of groups) {
    if (!detail) continue
    for (const url of detail.images) urls.add(url)
    if ('videos' in detail) for (const url of (detail as TaskDetail).videos) urls.add(url)
  }
  return [...urls]
}

export async function registerMedia(env: Env, envelope: Envelope): Promise<void> {
  if (!env.ADMIN_SECRET) return
  await Promise.all(mediaUrls(envelope).filter(isSourceMedia).map(async (url) => {
    const id = await digest(env.ADMIN_SECRET + '\n' + url)
    await env.SKYDAILY_KV.put('media:' + id, url, { expirationTtl: 40 * 86400 })
  }))
}

export async function publicEnvelope(env: Env, original: Envelope, origin: string): Promise<Envelope> {
  const copy = structuredClone(original)
  const memo = new Map<string, Promise<string[]>>()
  function media(url: string): Promise<string[]> {
    if (!memo.has(url)) memo.set(url, resolveMedia(url))
    return memo.get(url)!
  }
  async function resolveMedia(url: string): Promise<string[]> {
    const parsed = new URL(url)
    if (env.HISTORY_BASE_URL) {
      const base = new URL(env.HISTORY_BASE_URL.replace(/\/$/, '') + '/')
      if (parsed.origin === base.origin && parsed.pathname.startsWith(base.pathname + 'media/') && /^[a-f0-9]{24}\.webp$/.test(parsed.pathname.slice((base.pathname + 'media/').length)) && !parsed.search) return [parsed.href]
    }
    if (parsed.origin === origin && /^\/v1\/media\/[a-f0-9]{64}$/.test(parsed.pathname)) return [url]
    if (!env.ADMIN_SECRET || !isSourceMedia(url)) return []
    const id = await digest(env.ADMIN_SECRET + '\n' + url)
    return [origin + '/v1/media/' + id]
  }
  for (const task of copy.data.tasks) { task.text = cleanText(task.text); if (task.keyword) task.keyword = cleanText(task.keyword) }
  copy.errors = copy.errors.map(error => ({ section: cleanText(error.section), code: cleanText(error.code) }))
  for (const d of [...copy.data.taskDetails, ...copy.data.candles, copy.data.weather, copy.data.calendar]) {
    if (!d) continue
    d.images = (await Promise.all(d.images.map(media))).flat()
    if ('videos' in d) {
      const detail = d as TaskDetail
      detail.videos = (await Promise.all(detail.videos.map(media))).flat()
      const guideExpected = detail.keyword === seasonGuidePolicy.keyword && detail.links.length > 0
      detail.links = detail.keyword === seasonGuidePolicy.keyword ? [...new Set(detail.links.map(normalizeSeasonGuideLink).filter((url): url is string => url !== null))] : []
      if (guideExpected && !detail.links.length) throw new Error('unsupported_guide_link')
      detail.keyword = cleanText(detail.keyword)
      detail.title = cleanText(detail.title)
      detail.sections = detail.sections.map(s => ({ label: cleanText(s.label), text: cleanText(s.text) }))
    }
    if ('text' in d && typeof d.text === 'string') d.text = cleanText(d.text)
  }
  return copy
}
