import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

export function updatePreview(markdown, site, record, previewHash = '') {
  const base = new URL(site)
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash) throw new Error('SITE_URL must be a public HTTPS root')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(record.date)) throw new Error('Invalid record date')
  const version = createHash('sha256').update(JSON.stringify({ record, previewHash })).digest('hex').slice(0, 16)
  const home = base.href.replace(/\/$/, '') + '/'
  const image = `${home}preview/daily.webp?v=${record.date}-${version}`
  const block = `[![光遇每日任务 · ${record.date}](${image})](${home})`
  const pattern = /^\[!\[光遇每日任务 · \d{4}-\d{2}-\d{2}\]\(https:\/\/[^\s)]+\)\]\(https:\/\/[^\s)]+\)/
  if (!pattern.test(markdown)) throw new Error('README preview is missing')
  return markdown.replace(pattern, block)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const path = process.argv[2] ?? 'README.md'
  const site = process.env.SITE_URL
  if (!site) throw new Error('SITE_URL is required')
  const record = JSON.parse(await readFile('out/data.json', 'utf8'))
  const previewHash = createHash('sha256').update(await readFile('out/preview/daily.webp')).digest('hex')
  await writeFile(path, updatePreview(await readFile(path, 'utf8'), site, record, previewHash))
}
