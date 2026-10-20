import type { Env } from './env.ts'
import type { Envelope } from './schema/types.ts'
import { digest } from './public-data.ts'
import { readDaily } from './store/kv.ts'
import { envelopeSchema } from './schema/types.ts'

export async function contentVersion(envelope: Envelope): Promise<string> {
  return digest(JSON.stringify({ date: envelope.date, schema_version: envelope.schema_version, data: envelope.data, errors: envelope.errors }))
}

const BLOCKING_SECTION = (section: string): boolean => section === 'tasks' || section.startsWith('detail:')

/**
 * 只有任务与任务详情缺失会阻塞发布。天气、日历、季节蜡烛属于可选内容，
 * 上游当天没有给出时仍然发布，站点与 App 各自显示空态；错误仍会随数据一起记录。
 */
export function completeEnvelope(envelope: Envelope): boolean {
  if (envelope.errors.some((error) => BLOCKING_SECTION(error.section))) return false
  return envelopeSchema.safeParse(envelope).success
    && envelope.data.tasks.length > 0
    && envelope.data.tasks.every((task, index) => task.number === index + 1 && Boolean(task.text.trim())
      && (!task.keyword || envelope.data.taskDetails.some(detail => detail.keyword === task.keyword && Boolean(detail.text.trim() || detail.images.length || detail.sections.some(section => section.text.trim())))))
}

export async function dispatchEvent(env: Env, event: string, payload: Record<string, string>): Promise<void> {
  if (!env.GITHUB_REPOSITORY || !env.GITHUB_DISPATCH_TOKEN) throw new Error('publishing_not_configured')
  if (!/^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY)) throw new Error('invalid_publishing_repository')
  const response = await fetch(`https://api.github.com/repos/${env.GITHUB_REPOSITORY}/dispatches`, {
    method: 'POST',
    signal: AbortSignal.timeout(20_000),
    headers: { Authorization: 'Bearer ' + env.GITHUB_DISPATCH_TOKEN, Accept: 'application/vnd.github+json', 'User-Agent': 'SkyDaily-publisher', 'X-GitHub-Api-Version': '2026-03-10' },
    body: JSON.stringify({ event_type: event, client_payload: payload }),
  })
  if (response.status !== 204) throw new Error('publication_dispatch_failed')
}

export async function requestPublication(env: Env, envelope: Envelope): Promise<void> {
  if (!completeEnvelope(envelope)) return
  if (!env.GITHUB_REPOSITORY || !env.GITHUB_DISPATCH_TOKEN) throw new Error('publishing_not_configured')
  const version = await contentVersion(envelope)
  if (await env.SKYDAILY_KV.get('publish:complete:' + version) || await env.SKYDAILY_KV.get('publish:complete') === version) return
  await env.SKYDAILY_KV.put('snapshot:' + version, JSON.stringify(envelope), { expirationTtl: 7 * 86400 })
  const queue = env.UPDATE_COORDINATOR.get(env.UPDATE_COORDINATOR.idFromName('publication:' + version))
  const response = await queue.fetch('https://coordinator/publish', { method: 'POST', body: JSON.stringify({ version }) })
  if (!response.ok) throw new Error('publication_queue_failed')
}

export async function dispatchNotice(env: Env, hash: string): Promise<'done' | 'pending' | 'sent'> {
  if (await env.SKYDAILY_KV.get('publish:notice-complete:' + hash)) return 'done'
  const pendingRaw = await env.SKYDAILY_KV.get('publish:notice-pending')
  const pending = pendingRaw ? JSON.parse(pendingRaw) as { hash: string; at: number } : null
  if (pending?.hash === hash && Date.now() - pending.at < 20 * 60_000) return 'pending'
  await dispatchEvent(env, 'notice-updated', { notice: hash })
  await env.SKYDAILY_KV.put('publish:notice-pending', JSON.stringify({ hash, at: Date.now() }), { expirationTtl: 86400 })
  return 'sent'
}
export async function dispatchPublication(env: Env, version: string): Promise<'done' | 'pending' | 'sent'> {
  if (await env.SKYDAILY_KV.get('publish:complete:' + version) || await env.SKYDAILY_KV.get('publish:complete') === version) return 'done'
  const raw = await env.SKYDAILY_KV.get('snapshot:' + version)
  if (!raw) return 'done'
  const envelope = JSON.parse(raw) as Envelope
  const current = await readDaily(env, envelope.date) as Envelope | null
  if (current && completeEnvelope(current) && await contentVersion(current) !== version) return 'done'
  const pendingRaw = await env.SKYDAILY_KV.get('publish:pending')
  const pending = pendingRaw ? JSON.parse(pendingRaw) as { version: string; at: number } : null
  if (pending?.version === version && Date.now() - pending.at < 15 * 60_000) return 'pending'
  await dispatchEvent(env, 'daily-updated', { version })
  await env.SKYDAILY_KV.put('publish:pending', JSON.stringify({ version, at: Date.now() }))
  return 'sent'
}
