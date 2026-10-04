import { isSeasonGuideLink } from '../public-data.ts'
import type { Env } from '../env.ts'
import { envelopeSchema, type Envelope, type TaskDetail } from '../schema/types.ts'

export async function readPublishedHistory(env: Env, date: string): Promise<Envelope | null> {
  if (!env.HISTORY_BASE_URL) return null
  const base = new URL(env.HISTORY_BASE_URL.replace(/\/$/, '') + '/')
  if (base.protocol !== 'https:' || base.username || base.password) throw new Error('invalid_history_configuration')
  const response = await fetch(new URL(`daily/${date}.json`, base), { redirect: 'error', signal: AbortSignal.timeout(20_000) })
  if (response.status === 404) return null
  if (!response.ok) throw new Error('history_unavailable')
  const parsed = envelopeSchema.safeParse(await response.json())
  if (!parsed.success || parsed.data.date !== date) throw new Error('invalid_history_data')
  const envelope = parsed.data
  for (const detail of [...envelope.data.taskDetails, ...envelope.data.candles, envelope.data.weather, envelope.data.calendar]) {
    if (!detail) continue
    detail.images = detail.images.map(url => {
      const image = new URL(url)
      const prefix = base.pathname + 'media/'
      const filename = image.pathname.slice(prefix.length)
      if (image.origin !== base.origin || !image.pathname.startsWith(prefix) || !/^[a-f0-9]{24}\.webp$/.test(filename) || image.search || image.username || image.password) throw new Error('invalid_history_media')
      return new URL('media/' + filename, base).href
    })
    if ('links' in detail) {
      const guide = detail as TaskDetail
      if (guide.videos.length || guide.links.some(url => guide.keyword !== '季节蜡烛探索指南' || !isSeasonGuideLink(url))) throw new Error('invalid_history_links')
    }
  }
  return envelope
}
