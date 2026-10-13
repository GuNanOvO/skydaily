import { writeFile } from 'node:fs/promises'

function isVersion(value) {
  return Boolean(value)
    && typeof value.hash === 'string' && /^[a-f0-9]{64}$/.test(value.hash)
    && typeof value.title === 'string' && value.title.trim().length > 0
    && typeof value.message === 'string' && value.message.trim().length > 0
    && typeof value.published_at === 'string' && Number.isFinite(Date.parse(value.published_at))
    && typeof value.first_seen_at === 'string' && Number.isFinite(Date.parse(value.first_seen_at))
}

export function buildArchive(payload, generatedAt = new Date().toISOString()) {
  const notices = payload?.data?.notices
  if (!Array.isArray(notices)) throw new Error('notice history malformed')
  const versions = notices
    .filter(isVersion)
    .map(item => ({
      hash: item.hash,
      title: item.title,
      message: item.message,
      published_at: item.published_at,
      first_seen_at: item.first_seen_at,
    }))
    .sort((a, b) => a.first_seen_at.localeCompare(b.first_seen_at))
  const newest = versions.at(-1)
  const current = newest
    ? { hash: newest.hash, title: newest.title, published_at: newest.published_at, first_seen_at: newest.first_seen_at }
    : null
  return { schema: 1, generated_at: generatedAt, current, versions }
}

if (process.argv[1]?.endsWith('update-notices.mjs')) {
  const flag = name => {
    const index = process.argv.indexOf(name)
    return index === -1 ? undefined : process.argv[index + 1]
  }
  const base = (flag('--base') ?? process.env.WORKER_URL ?? '').replace(/\/$/, '')
  const out = flag('--out') ?? 'history/notices.json'
  if (!base) {
    console.error('需要 --base 或 WORKER_URL')
    process.exit(2)
  }
  const response = await fetch(base + '/v1/notice/history', { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(30_000) })
  if (response.status === 404) {
    console.warn('notice history unavailable: not found')
    process.exit(0)
  }
  if (!response.ok) throw new Error('notice history http ' + response.status)
  const archive = buildArchive(await response.json())
  await writeFile(out, JSON.stringify(archive, null, 2) + '\n')
  console.log('synced notices:', archive.versions.length, 'versions')
}
