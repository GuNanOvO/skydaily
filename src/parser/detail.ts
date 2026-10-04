import { isAdKeyword, isNoise, keepImage } from './common.ts'
import { collectImages, collectUrls, collectVideos, linesOf, plainOf, tokenizeAnswer } from './tokenize.ts'
import type { DetailSection, TaskDetail } from '../schema/types.ts'

export function extractDetailKeywords(rawAnswer: string): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const t of tokenizeAnswer(rawAnswer)) {
    if (t.kind !== 'link') continue
    if (isAdKeyword(t.question) || isAdKeyword(t.text)) continue
    if (seen.has(t.question)) continue
    seen.add(t.question)
    out.push(t.question)
  }
  return out
}

export function parseTaskDetail(keyword: string, rawAnswer: string): TaskDetail {
  const tokens = tokenizeAnswer(rawAnswer)
  const images = collectImages(tokens, keepImage)
  const videos = collectVideos(tokens)
  const links = collectUrls(tokens)

  let title = keyword
  const body: string[] = []
  for (const line of linesOf(tokens).map(plainOf)) {
    if (!line || isNoise(line)) continue
    const tm = /^【(.+)】$/.exec(line)
    if (tm && title === keyword && body.length === 0) {
      title = tm[1]
      continue
    }
    body.push(line)
  }

  const sections: DetailSection[] = []
  let current: DetailSection | null = null
  const intro: string[] = []
  for (const line of body) {
    const lm = /^(.{1,8}?)：\s*(.*)$/.exec(line)
    if (lm) {
      current = { label: lm[1], text: lm[2] }
      sections.push(current)
    } else if (current) {
      current.text += (current.text ? '\n' : '') + line
    } else {
      intro.push(line)
    }
  }
  if (intro.length > 0) sections.unshift({ label: '简介', text: intro.join('\n') })

  return { keyword, title, sections, images, videos, links, text: body.join('\n') }
}
