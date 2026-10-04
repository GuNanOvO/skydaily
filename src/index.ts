import { Hono } from 'hono'
import type { Context } from 'hono'
import { cors } from 'hono/cors'
import { etag } from 'hono/etag'
import type { Env } from './env.ts'
import { DATA_SOURCE, SCHEMA_VERSION, TAKEDOWN_CONTACT, TIMEZONE } from './config.ts'
import { readDaily, readRefreshMeta } from './store/kv.ts'
import { beijingDate, refreshAndStore, RefreshError } from './refresh.ts'
import type { Envelope } from './schema/types.ts'
import { publicEnvelope } from './public-data.ts'
import { readPublishedHistory } from './store/history.ts'
import { shouldRefreshAt } from './schedule.ts'
import { contentVersion } from './publishing.ts'
export { UpdateCoordinator } from './coordinator.ts'

const app = new Hono<{ Bindings: Env }>()

const CACHE_CONTROL = 'public, max-age=300'
const SECTIONS = ['tasks', 'weather', 'calendar', 'candles'] as const
type Section = (typeof SECTIONS)[number]

function deny(code: string, status: 429 | 503, retry: number): Response {
  return Response.json(
    { error: { code } },
    { status, headers: { 'Cache-Control': 'no-store', 'Retry-After': String(retry) } },
  )
}

async function admitRequest(c: Context<{ Bindings: Env }>): Promise<Response | null> {
  try {
    const ip = c.req.header('CF-Connecting-IP') ?? 'unknown'
    const { success } = await c.env.API_RATE_LIMIT.limit({ key: ip })
    return success ? null : deny('rate_limited', 429, 60)
  } catch {
    return deny('service_unavailable', 503, 60)
  }
}

const etagMiddleware = etag()

app.use('/v1/*', cors())
app.use('*', async (c, next) => {
  if (c.req.path.startsWith('/v1/') || c.req.path === '/healthz') {
    const denied = await admitRequest(c)
    if (denied) return denied
  }
  return next()
})
app.use('/v1/*', (c, next) => (c.req.path.startsWith('/v1/media/') ? next() : etagMiddleware(c, next)))

app.get('/', (c) =>
  c.json({
    name: 'skydaily',
    schema_version: SCHEMA_VERSION,
    timezone: TIMEZONE,
    source: DATA_SOURCE,
    takedown_contact: TAKEDOWN_CONTACT,
    endpoints: [
      'GET /healthz',
      'GET /v1/daily',
      'GET /v1/daily/tasks',
      'GET /v1/daily/tasks/:number',
      'GET /v1/daily/weather',
      'GET /v1/daily/calendar',
      'GET /v1/daily/candles',
      'GET /v1/daily/:date',
      'POST /admin/refresh',
    ],
  }),
)

app.get('/healthz', async (c) => {
  const refresh = await readRefreshMeta(c.env)
  const today = (await readDaily(c.env, beijingDate())) as Envelope | null
  const version = today ? await contentVersion(today) : null
  const published = version !== null && (await c.env.SKYDAILY_KV.get('publish:complete')) === version
  return c.json({ ok: true, refresh, today: today !== null, published })
})

function sendEnvelope(
  c: Context<{ Bindings: Env }>,
  envelope: Envelope,
  cached: boolean,
  data: unknown = envelope.data,
) {
  return c.json(
    {
      schema_version: envelope.schema_version,
      date: envelope.date,
      timezone: envelope.timezone,
      meta: { ...envelope.meta, cached },
      errors: envelope.errors,
      data,
    },
    200,
    { 'Cache-Control': CACHE_CONTROL },
  )
}

interface Loaded {
  envelope: Envelope
  cached: boolean
}

async function loadDaily(c: Context<{ Bindings: Env }>, date: string): Promise<Loaded | null> {
  const age = (Date.parse(beijingDate() + 'T00:00:00Z') - Date.parse(date + 'T00:00:00Z')) / 86400_000
  if (age < 0 || age > 29) return null
  const stored = (await readDaily(c.env, date)) as Envelope | null
  if (stored) return { envelope: stored, cached: true }
  if (date !== beijingDate()) {
    const historical = await readPublishedHistory(c.env, date)
    return historical ? { envelope: historical, cached: true } : null
  }
  return null
}

async function serve(c: Context<{ Bindings: Env }>, date: string, section?: Section) {
  const loaded = await loadDaily(c, date)
  if (!loaded) return c.json({ error: { code: 'not_found' } }, 404)
  const safe = await publicEnvelope(c.env, loaded.envelope, new URL(c.req.url).origin)
  return sendEnvelope(c, safe, loaded.cached, section ? safe.data[section] : undefined)
}

for (const s of SECTIONS) {
  app.get(`/v1/daily/${s}`, (c) => serve(c, beijingDate(), s))
}

app.get('/v1/daily', (c) => serve(c, beijingDate()))

app.get('/v1/daily/tasks/:number', async (c) => {
  const raw = c.req.param('number')
  if (!/^\d+$/.test(raw)) return c.json({ error: { code: 'bad_number' } }, 400)
  const loaded = await loadDaily(c, beijingDate())
  if (!loaded) return c.json({ error: { code: 'not_found' } }, 404)
  const safe = await publicEnvelope(c.env, loaded.envelope, new URL(c.req.url).origin)
  const { tasks, taskDetails } = safe.data
  const task = tasks.find((t) => t.number === Number(raw))
  if (!task) return c.json({ error: { code: 'not_found' } }, 404)
  const detail = task.keyword ? (taskDetails.find((d) => d.keyword === task.keyword) ?? null) : null
  return sendEnvelope(c, safe, loaded.cached, { task, detail })
})

app.get('/v1/media/:id', async c => {
  const id = c.req.param('id')
  if (!/^[a-f0-9]{64}$/.test(id)) return c.notFound()
  const url = await c.env.SKYDAILY_KV.get('media:' + id)
  if (!url) return c.notFound()
  const target = new URL(url)
  if (target.protocol !== 'https:' || target.hostname !== 'ok.166.net' || target.port || target.username || target.password) return c.notFound()
  const response = await fetch(url, { redirect: 'manual', headers: c.req.header('range') ? { Range: c.req.header('range')! } : {} })
  if (response.status >= 300 && response.status < 400) return c.notFound()
  if (!response.ok) return c.json({ error: { code: 'media_unavailable' } }, 502)
  const type = response.headers.get('content-type') ?? ''
  if (!/^(image\/(?:jpeg|png|webp|gif)|video\/mp4)(?:;|$)/i.test(type)) return c.notFound()
  const headers = new Headers({ 'Content-Type': type, 'Cache-Control': 'public, max-age=86400', 'X-Content-Type-Options': 'nosniff' })
  for (const name of ['content-length', 'content-range', 'accept-ranges']) {
    const value = response.headers.get(name)
    if (value) headers.set(name, value)
  }
  return new Response(response.body, { status: response.status, headers })
})

app.get('/v1/daily/:date', async (c) => {
  const date = c.req.param('date')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(Date.parse(date + 'T00:00:00Z')) || new Date(date + 'T00:00:00Z').toISOString().slice(0, 10) !== date) return c.json({ error: { code: 'bad_date' } }, 400)
  return serve(c, date)
})

async function safeEqual(a: string, b: string): Promise<boolean> {
  const da = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(a))
  const db = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(b))
  const timingSafeEqual = (
    crypto.subtle as unknown as { timingSafeEqual?: (x: ArrayBuffer, y: ArrayBuffer) => boolean }
  ).timingSafeEqual
  if (timingSafeEqual) return timingSafeEqual.call(crypto.subtle, da, db)
  const va = new Uint8Array(da)
  const vb = new Uint8Array(db)
  let diff = 0
  for (let i = 0; i < va.length; i++) diff |= va[i] ^ vb[i]
  return diff === 0
}

app.use('/admin/*', async (c, next) => {
  const auth = c.req.header('authorization') ?? ''
  const provided = auth.startsWith('Bearer ') ? auth.slice(7) : ''
  if (!c.env.ADMIN_SECRET || !provided || !(await safeEqual(provided, c.env.ADMIN_SECRET))) {
    return c.json({ error: { code: 'unauthorized' } }, 401)
  }
  await next()
})

app.get('/admin/snapshot/:version', async c => {
  const version = c.req.param('version')
  if (version === 'latest') {
    const envelope = await readDaily(c.env, beijingDate())
    return envelope ? c.json(envelope, 200, { 'Cache-Control': 'no-store' }) : c.json({ error: { code: 'not_found' } }, 404)
  }
  if (!/^[a-f0-9]{64}$/.test(version)) return c.json({ error: { code: 'bad_version' } }, 400)
  const raw = await c.env.SKYDAILY_KV.get('snapshot:' + version)
  return raw ? c.body(raw, 200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }) : c.json({ error: { code: 'not_found' } }, 404)
})

app.post('/admin/published/:version', async c => {
  const version = c.req.param('version')
  if (!/^[a-f0-9]{64}$/.test(version)) return c.json({ error: { code: 'bad_version' } }, 400)
  const raw = await c.env.SKYDAILY_KV.get('snapshot:' + version)
  if (!raw) return c.json({ error: { code: 'bad_version' } }, 400)
  const snapshot = JSON.parse(raw) as Envelope
  await c.env.SKYDAILY_KV.put('publish:complete:' + version, '1', { expirationTtl: 7 * 86400 })
  const current = await readDaily(c.env, beijingDate()) as Envelope | null
  if (current && await contentVersion(current) === version && snapshot.date === current.date) await c.env.SKYDAILY_KV.put('publish:complete', version)
  return c.json({ ok: true })
})

app.post('/admin/refresh', async c => {
  const envelope = await refreshAndStore(c.env, { signal: AbortSignal.timeout(120_000) })
  return c.json({ ok: true, date: envelope.date, meta: envelope.meta, errors: envelope.errors })
})

app.get('/admin/collection', async c => {
  const coordinator = c.env.UPDATE_COORDINATOR.get(c.env.UPDATE_COORDINATOR.idFromName('collection:' + beijingDate()))
  return coordinator.fetch('https://coordinator/status')
})

app.onError((err, c) => {
  if (err instanceof RefreshError) {
    return c.json({ error: { code: err.code } }, err.code === 'validation_failed' ? 500 : 502)
  }
  console.error('request failed:', err instanceof Error ? `${err.message}\n${err.stack ?? ''}` : err)
  return c.json({ error: { code: 'internal' } }, 500)
})

export { app }

export default {
  fetch: app.fetch,
  async scheduled(event: ScheduledController, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil((async () => {
      const local = new Date(event.scheduledTime + 8 * 3600_000)
      const minute = local.getUTCHours() * 60 + local.getUTCMinutes()
      const date = beijingDate(event.scheduledTime)
      const coordinator = env.UPDATE_COORDINATOR.get(env.UPDATE_COORDINATOR.idFromName('collection:' + date))
      if (minute <= 10) {
        const result = await coordinator.fetch('https://coordinator/night', { method: 'POST', body: JSON.stringify({ date }) })
        if (!result.ok) throw new Error('schedule_unavailable')
      } else if (await shouldRefreshAt(event.scheduledTime, env.ADMIN_SECRET)) {
        const result = await coordinator.fetch('https://coordinator/day', { method: 'POST', body: JSON.stringify({ date, slot: minute }) })
        if (!result.ok) throw new Error('schedule_unavailable')
      }
    })().catch(() => console.error('scheduled update incomplete')))
  },
}
