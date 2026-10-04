import { keepImage } from './common.ts'
import { collectImages, linesOf, plainOf, tokenizeAnswer } from './tokenize.ts'
import type { Weather } from '../schema/types.ts'

export function parseWeather(rawAnswer: string): Weather | null {
  const tokens = tokenizeAnswer(rawAnswer)
  const images = collectImages(tokens, keepImage)
  for (const line of linesOf(tokens).map(plainOf)) {
    const m = /天气播报[：:]\s*(.+)/.exec(line)
    if (m) return { text: m[1].trim(), images }
  }
  return null
}
