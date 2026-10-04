import {
  CALENDAR_QUESTION,
  CANDLES_QUESTIONS,
  DEFAULT_JITTER_MS,
  SCHEMA_VERSION,
  TASKS_QUESTION,
  TIMEZONE,
  WEATHER_QUESTION,
} from './config.ts'
import { fetchToken } from './upstream/token.ts'
import { queryKnowledge } from './upstream/sprite.ts'
import { extractDetailKeywords, parseTaskDetail } from './parser/detail.ts'
import { parseDailyTasks, parseSeasonCandleMap } from './parser/tasks.ts'
import { parseWeather } from './parser/weather.ts'
import { parseCalendar } from './parser/calendar.ts'
import { registerMedia } from './public-data.ts'
import { dailyDataSchema, type DailyData, type Envelope, type SectionError } from './schema/types.ts'
import { completeEnvelope, requestPublication } from './publishing.ts'
import { writeDaily, writeRefreshMeta } from './store/kv.ts'
import type { Env } from './env.ts'

export type ErrorCode = 'token_unavailable' | 'upstream_error' | 'empty_result' | 'validation_failed'

export class RefreshError extends Error {
  readonly code: ErrorCode
  constructor(code: ErrorCode) {
    super(code)
    this.code = code
  }
}

export interface RefreshResult {
  envelope: Envelope
}

export type Sleep = (ms: number) => Promise<void>

export interface RefreshOptions {
  sleep?: Sleep
  signal?: AbortSignal
}

const realSleep: Sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function jitterRange(spec = DEFAULT_JITTER_MS): [number, number] | null {
  const value = spec.trim()
  if (value === '0') return null
  const matched = /^(\d+)\s*-\s*(\d+)$/.exec(value)
  if (!matched) return [400, 1600]
  const min = Number(matched[1])
  return [min, Math.max(min, Number(matched[2]))]
}

function createPauser(env: Env, sleep: Sleep = realSleep): () => Promise<void> {
  const range = jitterRange(env.REFRESH_JITTER_MS)
  if (!range) return async () => {}
  const [min, max] = range
  return async () => {
    await sleep(min + Math.floor(Math.random() * (max - min + 1)))
  }
}

export function beijingDate(now = Date.now()): string {
  return new Date(now + 8 * 3600_000).toISOString().slice(0, 10)
}

export async function runRefresh(env: Env, options: RefreshOptions = {}): Promise<RefreshResult> {
  const date = beijingDate()
  const pauser = createPauser(env, options.sleep)
  const pause = async () => { options.signal?.throwIfAborted(); await pauser(); options.signal?.throwIfAborted() }
  let token: string
  try {
    token = await fetchToken(env, options.signal)
  } catch (e) {
    console.error('refresh: token failed')
    throw new RefreshError('token_unavailable')
  }

  const errors: SectionError[] = []
  const data = {
    tasks: [],
    taskDetails: [],
    weather: null,
    calendar: null,
    candles: [],
    seasonCandleMap: null,
  } as {
    tasks: DailyData['tasks']
    taskDetails: DailyData['taskDetails']
    weather: DailyData['weather']
    calendar: DailyData['calendar']
    candles: DailyData['candles']
    seasonCandleMap: DailyData['seasonCandleMap']
  }

  let keywords: string[] = []
  try {
    await pause()
    const res = await queryKnowledge(env, token, TASKS_QUESTION, 'link', options.signal)
    data.tasks = parseDailyTasks(res.answer)
    data.seasonCandleMap = parseSeasonCandleMap(res.answer)
    keywords = extractDetailKeywords(res.answer)
  } catch (e) {
    console.error('refresh: tasks failed')
    errors.push({ section: 'tasks', code: 'upstream_error' })
  }
  if (data.tasks.length === 0 && !errors.some((x) => x.section === 'tasks')) {
    errors.push({ section: 'tasks', code: 'empty_result' })
  }

  for (const kw of keywords) {
    try {
      await pause()
      const res = await queryKnowledge(env, token, kw, 'link', options.signal)
      data.taskDetails.push(parseTaskDetail(kw, res.answer))
    } catch (e) {
      console.error('refresh: detail failed')
      errors.push({ section: `detail:${kw}`, code: 'upstream_error' })
    }
  }

  try {
    await pause()
    const res = await queryKnowledge(env, token, WEATHER_QUESTION, 'hotNews', options.signal)
    data.weather = parseWeather(res.answer)
    if (!data.weather) errors.push({ section: 'weather', code: 'empty_result' })
  } catch (e) {
    console.error('refresh: weather failed')
    errors.push({ section: 'weather', code: 'upstream_error' })
  }

  try {
    await pause()
    const res = await queryKnowledge(env, token, CALENDAR_QUESTION, 'link', options.signal)
    data.calendar = parseCalendar(res.answer)
    if (!data.calendar) errors.push({ section: 'calendar', code: 'empty_result' })
  } catch (e) {
    console.error('refresh: calendar failed')
    errors.push({ section: 'calendar', code: 'upstream_error' })
  }

  for (const q of CANDLES_QUESTIONS) {
    try {
      await pause()
      const res = await queryKnowledge(env, token, q, 'link', options.signal)
      data.candles.push(parseTaskDetail(q, res.answer))
    } catch (e) {
      console.error('refresh: candles failed')
      errors.push({ section: `candles:${q}`, code: 'upstream_error' })
    }
  }
  if (CANDLES_QUESTIONS.length > 0 && data.candles.length === 0) {
    errors.push({ section: 'candles', code: 'empty_result' })
  }

  const parsed = dailyDataSchema.safeParse(data)
  if (!parsed.success) {
    console.error('refresh: schema validation failed')
    throw new RefreshError('validation_failed')
  }

  const envelope: Envelope = {
    schema_version: SCHEMA_VERSION,
    date,
    timezone: TIMEZONE,
    data: parsed.data,
    meta: { fetched_at: new Date().toISOString(), cached: false, stale: false },
    errors,
  }
  return { envelope }
}

export async function refreshAndStore(env: Env, options: RefreshOptions = {}): Promise<Envelope> {
  const { envelope } = await runRefresh(env, options)
  await storeRefresh(env, envelope)
  return envelope
}

export async function storeRefresh(env: Env, envelope: Envelope): Promise<boolean> {
  if (!completeEnvelope(envelope)) throw new RefreshError('validation_failed')
  await registerMedia(env, envelope)
  await writeDaily(env, envelope.date, envelope)
  await writeRefreshMeta(env, {
    last_refresh: envelope.meta.fetched_at,
    date: envelope.date,
    errors: envelope.errors,
  })
  try { await requestPublication(env, envelope); return true }
  catch { console.error('publication queue pending'); return false }
}
