import { readFile, stat, readdir } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { envelopeSchema } from '../src/schema/types.ts'
import { completeEnvelope } from '../src/publishing.ts'
import { normalizeSeasonGuideLink, seasonGuidePolicy } from '../src/link-policy.mjs'

const root = resolve(process.argv.find(arg => arg.startsWith('--directory='))?.split('=')[1] ?? 'out')
const site = new URL(process.env.SITE_URL ?? 'https://skydaily.nankki.com')
const archive = join(root, 'history')
const fail = message => { throw new Error(message) }
const json = async path => JSON.parse(await readFile(path, 'utf8'))
const dateValue = date => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) fail('Invalid archive date')
  const value = Date.parse(date + 'T00:00:00Z')
  if (!Number.isFinite(value) || new Date(value).toISOString().slice(0, 10) !== date) fail('Invalid archive date')
  return value
}
const references = new Set()
function checkRecord(record) {
  const result = envelopeSchema.safeParse(record)
  if (!result.success) fail('Record does not match the public data schema')
  dateValue(record.date)
  if (record.schema_version !== 1) fail('Unsupported schema version')
  if (record.data.tasks.length !== 4 || record.data.tasks.some((task, i) => task.number !== i + 1 || !task.text.trim())) fail('Invalid daily task list')
  for (const task of record.data.tasks) {
    if (task.keyword && !record.data.taskDetails.some(detail => detail.keyword === task.keyword)) fail('Task detail reference is missing')
  }
  const copy = structuredClone(record)
  for (const group of [...copy.data.taskDetails, ...copy.data.candles, copy.data.weather, copy.data.calendar]) {
    if (!group) continue
    for (const image of group.images) {
      const url = new URL(image)
      if (url.origin !== site.origin || url.username || url.password || url.search || url.hash || !/^\/history\/media\/[a-f0-9]{24}\.webp$/.test(url.pathname)) fail('Image URL is outside the public archive')
      references.add(url.pathname.slice('/history/'.length))
    }
    group.images = []
    if (group.videos?.length) fail('Source videos are not allowed in the static archive')
    for (const link of group.links ?? []) {
      if (group.keyword !== seasonGuidePolicy.keyword || normalizeSeasonGuideLink(link) !== link) fail('External link is not allowed in public data')
    }
    if (group.links) group.links = []
  }
  if (/https?:\/\//i.test(JSON.stringify(copy))) fail('Public text contains an unapproved source URL')
  const inspect = value => {
    if (!value || typeof value !== 'object') return
    for (const [key, child] of Object.entries(value)) {
      if (/^(raw|source|source_url|token|secret|authorization|cookie)$/i.test(key)) fail('Private field found in public data')
      inspect(child)
    }
  }
  inspect(record)
}

try {
  const index = await json(join(archive, 'index.json'))
  if (!Array.isArray(index.dates) || !index.dates.length || index.dates.length > 30 || index.date_count !== index.dates.length) fail('Invalid archive index')
  const dates = index.dates.map(entry => entry.date)
  if (new Set(dates).size !== dates.length || dates.some((date, i) => i && date <= dates[i - 1])) fail('Archive dates are not unique and ordered')
  if (index.from !== dates[0] || index.to !== dates.at(-1) || dateValue(dates.at(-1)) - dateValue(dates[0]) > 29 * 86400000) fail('Archive window exceeds 30 days')
  for (const entry of index.dates) {
    if (entry.file !== `daily/${entry.date}.json`) fail('Invalid archive file path')
    const record = await json(join(archive, entry.file))
    if (record.date !== entry.date) fail('Archive record date does not match its index')
    checkRecord(record)
    await stat(join(root, 'archive', entry.date + '.html'))
    const exported = await json(join(root, 'archive', entry.date + '.json'))
    checkRecord(exported)
    if (JSON.stringify(exported) !== JSON.stringify(record)) fail('Published archive does not match its record')
  }
  const latest = await json(join(root, 'data.json'))
  checkRecord(latest)
  if (latest.date !== index.to) fail('Homepage data is not the latest record')
  if (!completeEnvelope(latest)) fail('Latest publication is incomplete')
  if (JSON.stringify(latest) !== JSON.stringify(await json(join(archive, `daily/${latest.date}.json`)))) fail('Homepage data does not match its archive')
  for (const file of references) {
    const bytes = await readFile(join(archive, file))
    if (bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WEBP') fail('Image is missing or is not WebP')
  }
  const mediaFiles = await readdir(join(archive, 'media'))
  if (mediaFiles.length !== references.size || mediaFiles.some(name => !references.has('media/' + name))) fail('Archive contains unreferenced media')
  const documents = [join(root, 'index.html'), join(root, 'api.html'), ...dates.map(date => join(root, 'archive', date + '.html'))]
  for (const file of documents) {
    const html = await readFile(file, 'utf8')
    if (/https?:\/\/(?:ok\.166\.net|raw\.githubusercontent\.com)/i.test(html) || /(?:authorization|admin_secret|access_token)\s*[:=]/i.test(html)) fail('Published HTML contains a private source or credential')
  }
  if (process.argv.includes('--preview')) {
    const bytes = await readFile(join(root, 'preview/daily.webp'))
    if (bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WEBP') fail('README preview is missing or is not WebP')
    const share = await readFile(join(root, 'preview/share.jpg'))
    if (share[0] !== 0xff || share[1] !== 0xd8 || share[2] !== 0xff) fail('Share card is missing or is not JPEG')
  }
  console.log(`Publication content valid: ${dates.length} dates, ${references.size} images`)
} catch (error) {
  console.error(error instanceof Error && !/https?:|secret=|token=/i.test(error.message) ? error.message : 'Publication content validation failed')
  process.exitCode = 1
}
