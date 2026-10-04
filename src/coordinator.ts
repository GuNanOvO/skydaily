import type { Env } from './env.ts'
import type { Envelope } from './schema/types.ts'
import { beijingDate, runRefresh, storeRefresh } from './refresh.ts'
import { readDaily } from './store/kv.ts'
import { digest } from './public-data.ts'
import { completeEnvelope, dispatchEvent, dispatchPublication, requestPublication } from './publishing.ts'
import { midnightWindow, randomDelay } from './schedule.ts'

type Collection = {
  kind: 'collection'; date: string; phase: 'waiting' | 'running' | 'success' | 'failed';
  attempts: number; due: number; deadline: number; baseline: string | null;
  noticeDue?: number; notices?: number; notified?: boolean; daySlots: number[];
}
type Publication = { kind: 'publication'; version: string; due: number; attempts: number; phase: 'waiting' | 'done' | 'failed' }
type State = Collection | Publication
export class UpdateCoordinator {
  private busy = false
  private ctx: DurableObjectState
  private env: Env
  constructor(ctx: DurableObjectState, env: Env) {
    this.ctx = ctx
    this.env = env
  }

  async fetch(request: Request): Promise<Response> {
    const path = new URL(request.url).pathname
    if (path === '/status') {
      const state = await this.ctx.storage.get<State>('state')
      if (!state || state.kind !== 'collection') return Response.json({ status: 'not_started' })
      return Response.json({ date: state.date, status: state.phase, attempts: state.attempts })
    }
    const input = await request.json() as { date?: string; version?: string; slot?: number }
    if (path === '/publish' && input.version && /^[a-f0-9]{64}$/.test(input.version)) {
      await this.ctx.blockConcurrencyWhile(async () => {
        const current = await this.ctx.storage.get<Publication>('state')
        if (current?.phase === 'waiting') return
        const state: Publication = { kind: 'publication', version: input.version!, phase: 'waiting', due: Date.now() + randomDelay(2 * 60_000, 8 * 60_000), attempts: 0 }
        await this.save(state, state.due)
      })
      return Response.json({ ok: true })
    }
    if (!input.date || input.date !== beijingDate()) return new Response(null, { status: 400 })
    if (path === '/night') {
      await this.ctx.blockConcurrencyWhile(async () => {
        if (await this.ctx.storage.get('state')) return
        const bounds = midnightWindow(input.date!)
        const previous = await readDaily(this.env, beijingDate(bounds.start - 1)) as Envelope | null
        const baseline = previous && completeEnvelope(previous) ? await digest(JSON.stringify(previous.data)) : null
        const firstStart = Math.max(Date.now(), bounds.start)
        const state: Collection = { kind: 'collection', date: input.date!, phase: 'waiting', attempts: 0, due: firstStart + randomDelay(0, Math.max(0, bounds.firstEnd - firstStart)), deadline: bounds.deadline, baseline, daySlots: [] }
        if (firstStart > bounds.firstEnd) { await this.failNight(state); return }
        await this.save(state, Math.min(state.due, state.deadline))
      })
      return Response.json({ ok: true })
    }
    if (path === '/day' && typeof input.slot === 'number') {
      const current = await this.ctx.storage.get<State>('state')
      let state: Collection
      if (!current) {
        state = { kind: 'collection', date: input.date, phase: 'failed', attempts: 0, due: 0, deadline: midnightWindow(input.date).deadline, baseline: null, daySlots: [] }
      } else if (current.kind === 'collection') state = current
      else return new Response(null, { status: 400 })
      if (this.busy || state.daySlots.includes(input.slot)) return Response.json({ ok: true })
      this.busy = true
      try {
        state.daySlots.push(input.slot)
        await this.ctx.storage.put('state', state)
        const { envelope } = await runRefresh(this.env, { signal: AbortSignal.timeout(120_000) })
        if (envelope.date === state.date && completeEnvelope(envelope)
          && (!state.baseline || await digest(JSON.stringify(envelope.data)) !== state.baseline)) {
          const queued = await storeRefresh(this.env, envelope)
          state.phase = 'success'
          delete state.noticeDue
          if (!queued) state.noticeDue = Date.now() + randomDelay(2 * 60_000, 8 * 60_000)
          await this.save(state, state.noticeDue)
        }
      } catch { console.error('daytime update incomplete') }
      finally { this.busy = false }
      return Response.json({ ok: true })
    }
    return new Response(null, { status: 404 })
  }

  private async save(state: State, alarm?: number): Promise<void> {
    await this.ctx.storage.put('state', state)
    if (alarm !== undefined) await this.ctx.storage.setAlarm(alarm)
    else await this.ctx.storage.deleteAlarm()
  }

  async alarm(): Promise<void> {
    const state = await this.ctx.storage.get<State>('state')
    if (!state) return
    if (state.kind === 'publication') {
      if (state.phase !== 'waiting') return
      if (Date.now() < state.due) { await this.ctx.storage.setAlarm(state.due); return }
      try {
        const result = await dispatchPublication(this.env, state.version)
        if (result === 'done') { state.phase = 'done'; await this.save(state); return }
      } catch { console.error('publication notification pending') }
      state.attempts++
      if (state.attempts >= 6) { state.phase = 'failed'; await this.save(state); return }
      state.due = Date.now() + randomDelay(16 * 60_000, 22 * 60_000)
      await this.save(state, state.due)
      return
    }
    if (state.phase === 'success') {
      if (!state.noticeDue) return
      if (Date.now() < state.noticeDue) { await this.ctx.storage.setAlarm(state.noticeDue); return }
      try {
        const envelope = await readDaily(this.env, state.date) as Envelope | null
        if (envelope && completeEnvelope(envelope)) await requestPublication(this.env, envelope)
        delete state.noticeDue
        await this.save(state)
      } catch {
        state.noticeDue = Date.now() + randomDelay(2 * 60_000, 8 * 60_000)
        await this.save(state, state.noticeDue)
      }
      return
    }
    if (state.phase === 'failed') {
      if (state.notified || !state.noticeDue) return
      if (Date.now() < state.noticeDue) { await this.ctx.storage.setAlarm(state.noticeDue); return }
      try {
        await dispatchEvent(this.env, 'collection-failed', { date: state.date })
        state.notified = true
        await this.save(state)
      } catch {
        state.notices = (state.notices ?? 0) + 1
        if (state.notices < 4) { state.noticeDue = Date.now() + randomDelay(2 * 60_000, 8 * 60_000); await this.save(state, state.noticeDue) }
        else await this.save(state)
      }
      return
    }
    if (Date.now() >= state.deadline) { await this.failNight(state); return }
    if (Date.now() < state.due) { await this.ctx.storage.setAlarm(state.due); return }
    if (this.busy) { await this.ctx.storage.setAlarm(Math.min(Date.now() + 60_000, state.deadline)); return }
    this.busy = true
    try {
      state.phase = 'running'
      state.attempts++
      await this.save(state, state.deadline)
      const { envelope } = await runRefresh(this.env, { signal: AbortSignal.timeout(Math.max(1, Math.min(120_000, state.deadline - Date.now()))) })
      if (Date.now() < state.deadline && envelope.date === state.date && completeEnvelope(envelope)
        && (!state.baseline || await digest(JSON.stringify(envelope.data)) !== state.baseline)) {
        const queued = await storeRefresh(this.env, envelope)
        state.phase = 'success'
        if (!queued) state.noticeDue = Date.now() + randomDelay(2 * 60_000, 8 * 60_000)
        await this.save(state, state.noticeDue)
        return
      }
    } catch { console.error('night update incomplete') }
    finally { this.busy = false }
    if (Date.now() >= state.deadline) { await this.failNight(state); return }
    state.phase = 'waiting'
    state.due = Math.min(state.deadline, Date.now() + randomDelay(2 * 60_000, 6 * 60_000))
    await this.save(state, state.due)
  }

  private async failNight(state: Collection): Promise<void> {
    state.phase = 'failed'
    state.noticeDue = Date.now() + randomDelay(2 * 60_000, 8 * 60_000)
    await this.save(state, state.noticeDue)
  }
}
