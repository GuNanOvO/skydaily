import { beijingDate } from './refresh.ts'
const WINDOWS = [[5 * 60, 9 * 60], [10 * 60, 15 * 60], [16 * 60, 22 * 60]] as const
export async function refreshMinutes(date: string, seed: string): Promise<number[]> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`skydaily-schedule:${date}:${seed}`))
  const view = new DataView(digest)
  return WINDOWS.map(([start, end], index) => start + (view.getUint32(index * 4) % ((end - start) / 10)) * 10)
}
export async function shouldRefreshAt(scheduledTime: number, seed: string): Promise<boolean> {
  const local = new Date(scheduledTime + 8 * 3600_000)
  const minute = local.getUTCHours() * 60 + local.getUTCMinutes()
  if (!WINDOWS.some(([start, end]) => minute >= start && minute < end)) return false
  return (await refreshMinutes(beijingDate(scheduledTime), seed)).includes(minute)
}

export function randomDelay(minimum: number, maximum: number): number {
  const value = crypto.getRandomValues(new Uint32Array(1))[0]
  return minimum + value % (maximum - minimum + 1)
}

export function midnightWindow(date: string): { start: number; firstEnd: number; deadline: number } {
  const start = Date.parse(date + 'T00:00:00+08:00')
  return { start, firstEnd: start + 15 * 60_000, deadline: start + 60 * 60_000 }
}
