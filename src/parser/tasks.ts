import { isAdKeyword } from './common.ts'
import { linesOf, plainOf, tokenizeAnswer } from './tokenize.ts'
import type { Task } from '../schema/types.ts'

const SEASON_CANDLE_RE = /今日季节蜡烛所在地图[：:]/

export function parseDailyTasks(rawAnswer: string): Task[] {
  const tasks: Task[] = []
  for (const line of linesOf(tokenizeAnswer(rawAnswer))) {
    const plain = plainOf(line)
    if (!/^\d+[.、]/.test(plain)) continue
    if (SEASON_CANDLE_RE.test(plain)) continue

    let text = ''
    let keyword: string | null = null
    for (const t of line) {
      if (t.kind === 'link') {
        keyword = isAdKeyword(t.question) ? null : t.question
        break
      }
      if (t.kind === 'url' || t.kind === 'image' || t.kind === 'video') break
      if (t.kind === 'text') text += t.text
      else if (t.kind === 'space') text += ' '
    }
    text = text
      .replace(/^\d+[.、]\s*/, '')
      .replace(/[>》\s\u3000]+$/, '')
      .trim()
    if (!text || isAdKeyword(text)) continue
    tasks.push({ number: Number(plain.match(/^(\d+)/)![1]), text, keyword })
  }
  return tasks
}

export function parseSeasonCandleMap(rawAnswer: string): string | null {
  for (const line of linesOf(tokenizeAnswer(rawAnswer))) {
    const plain = plainOf(line)
    if (!SEASON_CANDLE_RE.test(plain)) continue
    let text = ''
    for (const t of line) {
      if (t.kind === 'link' || t.kind === 'url' || t.kind === 'image' || t.kind === 'video') break
      if (t.kind === 'text') text += t.text
      else if (t.kind === 'space') text += ' '
    }
    const m = /今日季节蜡烛所在地图[：:]\s*(.*)/.exec(text)
    const map = m?.[1].replace(/[>》\s\u3000]+$/, '').trim()
    return map || null
  }
  return null
}
