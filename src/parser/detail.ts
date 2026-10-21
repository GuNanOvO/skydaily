import { isAdKeyword, isNoise, keepImage } from './common.ts'
import { collectImages, collectUrls, collectVideos, linesOf, plainOf, tokenizeAnswer } from './tokenize.ts'
import type { DetailVariant, DetailSection, TaskDetail } from '../schema/types.ts'

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

export interface DetailLink {
  question: string
  label: string
}

export function parseDetailLinks(rawAnswer: string): DetailLink[] {
  const seen = new Set<string>()
  const out: DetailLink[] = []
  for (const t of tokenizeAnswer(rawAnswer)) {
    if (t.kind !== 'link') continue
    if (isAdKeyword(t.question) || isAdKeyword(t.text)) continue
    if (!t.text.trim() || seen.has(t.question)) continue
    seen.add(t.question)
    out.push({ question: t.question, label: t.text.trim() })
  }
  return out
}

function parseBody(fallbackTitle: string, rawAnswer: string): Omit<DetailVariant, 'label'> {
  const tokens = tokenizeAnswer(rawAnswer)
  const images = collectImages(tokens, keepImage)
  const videos = collectVideos(tokens)
  const links = collectUrls(tokens)

  let title = fallbackTitle
  const body: string[] = []
  for (const line of linesOf(tokens).map(plainOf)) {
    if (!line || isNoise(line)) continue
    const tm = /^【(.+)】$/.exec(line)
    if (tm && title === fallbackTitle && body.length === 0) {
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

  return { title, sections, images, videos, links, text: body.join('\n') }
}

export function parseTaskDetail(keyword: string, rawAnswer: string): TaskDetail {
  return { keyword, ...parseBody(keyword, rawAnswer) }
}

export function parseDetailVariant(label: string, rawAnswer: string): DetailVariant {
  return { label, ...parseBody(label, rawAnswer) }
}
