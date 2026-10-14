import { readFile } from 'node:fs/promises'

const JSON_TARGETS = ['/data.json', '/history/index.json', '/history/notices.json']

export function warmTargets(index, record) {
  const targets = [...JSON_TARGETS]
  const newest = index?.dates?.at(-1)
  if (!newest) return targets
  targets.push('/history/daily/' + newest.date + '.json')
  if (!record) return targets
  const groups = [...(record.data?.taskDetails ?? []), ...(record.data?.candles ?? []), record.data?.weather, record.data?.calendar]
  const media = new Set()
  for (const group of groups) {
    if (!group) continue
    for (const url of group.images ?? []) {
      const match = /\/history\/media\/([a-f0-9]{24}\.webp)$/.exec(url)
      if (match) media.add(match[1])
    }
  }
  for (const name of [...media].sort()) {
    targets.push('/history/media/' + name, '/history/thumbs/' + name)
  }
  return targets
}

if (process.argv[1]?.endsWith('warm-cache.mjs')) {
  const site = (process.env.SITE_URL ?? 'https://skydaily.nankki.com').replace(/\/$/, '')
  const index = JSON.parse(await readFile('history/index.json', 'utf8'))
  const newest = index?.dates?.at(-1)
  let record = null
  if (newest) record = JSON.parse(await readFile('history/' + newest.file, 'utf8'))
  const targets = warmTargets(index, record)
  let ok = 0
  let failed = 0
  for (const path of targets) {
    try {
      const response = await fetch(site + path, { headers: { accept: '*/*', 'user-agent': 'skydaily-warm/1.0' }, signal: AbortSignal.timeout(20_000) })
      await response.arrayBuffer()
      if (response.ok) ok++
      else { failed++; console.warn('warm failed', path, response.status) }
    } catch (error) {
      failed++
      console.warn('warm failed', path, error instanceof Error ? error.message : String(error))
    }
  }
  console.log(`cache warm: ${ok}/${targets.length} ok`)
  if (failed > 0) console.log('warn: some targets stayed cold; the first visitor may pay the origin fetch')
}
