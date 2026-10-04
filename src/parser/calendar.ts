import { keepImage } from './common.ts'
import { collectImages, tokenizeAnswer } from './tokenize.ts'
import type { Calendar } from '../schema/types.ts'

export function parseCalendar(rawAnswer: string): Calendar | null {
  const images = collectImages(tokenizeAnswer(rawAnswer), keepImage)
  return images.length > 0 ? { images } : null
}
