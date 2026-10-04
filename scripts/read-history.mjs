import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
export function readHistory(date, {directory = fileURLToPath(new URL('../history/', import.meta.url)), assetBaseUrl} = {}) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('Invalid date')
  const read = file => JSON.parse(readFileSync(resolve(directory, file), 'utf8'))
  const index = read('index.json')
  const entry = index.dates.find(item => item.date === date)
  if (!entry) throw new Error('Date not archived: ' + date)
  const envelope = read(entry.file)
  if (!assetBaseUrl) return envelope
  const base = new URL(assetBaseUrl.endsWith('/') ? assetBaseUrl : assetBaseUrl + '/')
  if (!['http:', 'https:'].includes(base.protocol)) throw new Error('assetBaseUrl must be HTTP(S)')
  for (const detail of [...envelope.data.taskDetails, ...envelope.data.candles, envelope.data.weather, envelope.data.calendar]) {
    if (!detail) continue
    detail.images = detail.images.map(url => new URL('media/' + new URL(url).pathname.split('/').pop(), base).href)
  }
  return envelope
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify(readHistory(process.argv[2], {assetBaseUrl: process.argv[3]}), null, 2))
}
