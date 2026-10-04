export type Token =
  | { kind: 'text'; text: string }
  | { kind: 'break' }
  | { kind: 'space' }
  | { kind: 'color'; value: string }
  | { kind: 'link'; question: string; text: string }
  | { kind: 'url'; url: string }
  | { kind: 'image'; src: string }
  | { kind: 'video'; src: string }

const TOKEN_SOURCE =
  /#r|#n|#c(?<color>[0-9a-fA-F]{6})|<a\s[^>]*?\?q=(?<q>[^"]+)"[^>]*>(?<alink>[\s\S]*?)<\/a>|<a\s[^>]*>[\s\S]*?<\/a>|<img[^>]*?src="(?<img>[^"]+)"[^>]*>|<video>(?<video>[\s\S]*?)<\/video>|<image>(?<image>[\s\S]*?)<\/image>|<ask>(?<ask>[\s\S]*?)<\/ask>|<auto_blank>(?<h5>[\s\S]*?)<\/auto_blank>|<url>[\s\S]*?<\/url>/.source

const ENTITIES: Record<string, string> = {
  '&quot;': '"',
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&#39;': "'",
  '&nbsp;': ' ',
}

function decodeEntities(s: string): string {
  return s.replace(/&(?:quot|amp|lt|gt|#39|nbsp);/g, (m) => ENTITIES[m] ?? m)
}

function plainInner(s: string): string {
  return decodeEntities(s.replace(/<[^>]+>/g, ''))
    .replace(/#r|#n|#c[0-9a-fA-F]{6}/g, '')
    .trim()
}

export function tokenizeAnswer(raw: string): Token[] {
  const tokens: Token[] = []
  const re = new RegExp(TOKEN_SOURCE, 'g')
  let text = ''
  let last = 0

  const flush = () => {
    const cleaned = decodeEntities(text).replace(/<[^>]+>/g, '')
    if (cleaned.trim()) tokens.push({ kind: 'text', text: cleaned })
    text = ''
  }

  for (const m of raw.matchAll(re)) {
    text += raw.slice(last, m.index)
    last = m.index + m[0].length
    const g = m.groups ?? {}
    if (m[0] === '#r') {
      flush()
      tokens.push({ kind: 'break' })
    } else if (m[0] === '#n') {
      flush()
      tokens.push({ kind: 'space' })
    } else if (g.color !== undefined) {
      flush()
      tokens.push({ kind: 'color', value: g.color })
    } else if (g.q !== undefined) {
      flush()
      tokens.push({ kind: 'link', question: decodeEntities(g.q), text: plainInner(g.alink ?? '') })
    } else if (g.img !== undefined) {
      flush()
      tokens.push({ kind: 'image', src: g.img })
    } else if (g.video !== undefined) {
      flush()
      tokens.push({ kind: 'video', src: g.video.trim() })
    } else if (g.image !== undefined) {
      flush()
      tokens.push({ kind: 'image', src: g.image.trim() })
    } else if (g.ask !== undefined) {
      text += plainInner(g.ask)
    } else if (g.h5 !== undefined) {
      flush()
      tokens.push({ kind: 'url', url: g.h5.trim() })
    }
  }
  text += raw.slice(last)
  flush()
  return tokens
}

export function linesOf(tokens: Token[]): Token[][] {
  const lines: Token[][] = [[]]
  for (const t of tokens) {
    if (t.kind === 'break') lines.push([])
    else lines[lines.length - 1].push(t)
  }
  return lines
}

export function collectImages(tokens: Token[], keep: (src: string) => boolean): string[] {
  const out: string[] = []
  for (const t of tokens) if (t.kind === 'image' && keep(t.src)) out.push(t.src)
  return out
}

export function collectVideos(tokens: Token[]): string[] {
  const out: string[] = []
  for (const t of tokens) if (t.kind === 'video') out.push(t.src)
  return out
}

export function collectUrls(tokens: Token[]): string[] {
  const out: string[] = []
  for (const t of tokens) if (t.kind === 'url') out.push(t.url)
  return out
}

export function plainOf(line: Token[]): string {
  let s = ''
  for (const t of line) {
    if (t.kind === 'text') s += t.text
    else if (t.kind === 'link') s += t.text
    else if (t.kind === 'space') s += ' '
  }
  return s.replace(/[ \u3000]+/g, ' ').trim()
}
