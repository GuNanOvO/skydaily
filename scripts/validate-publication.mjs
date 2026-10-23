import { readFile, stat, readdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve, join } from 'node:path'
import { envelopeSchema } from '../src/schema/types.ts'
import { completeEnvelope } from '../src/publishing.ts'
import { normalizeNoticeLink, normalizeSeasonGuideLink, seasonGuidePolicy } from '../src/link-policy.mjs'
import { APP_SCREENSHOT_FILES, releaseObject } from '../src/app-release.mjs'
import { assertApkMatchesRelease } from '../src/apk-inspect.mjs'

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
  if (record.data.tasks.length < 1 || record.data.tasks.some((task, i) => task.number !== i + 1 || !task.text.trim())) fail('Invalid daily task list')
  for (const task of record.data.tasks) {
    if (task.keyword && !record.data.taskDetails.some(detail => detail.keyword === task.keyword)) fail('Task detail reference is missing')
  }
  const copy = structuredClone(record)
  for (const group of [...copy.data.taskDetails.flatMap(detail => [detail, ...(detail.variants ?? [])]), ...copy.data.candles, copy.data.weather, copy.data.calendar]) {
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

// 下载页存在时校验清单、安装包与页面三者一致；未发布时页面不得出现下载地址。
async function checkAppRelease() {
  let page
  try {
    page = await readFile(join(root, 'app/index.html'), 'utf8')
  } catch (error) {
    if (error?.code === 'ENOENT') return
    throw error
  }
  if (/github\.com|raw\.githubusercontent\.com/i.test(page)) fail('App page links to GitHub')
  if (/http:\/\//i.test(page)) fail('App page contains an insecure link')
  const readOrFail = async (file, message) => {
    try {
      return await readFile(file)
    } catch (error) {
      if (error?.code === 'ENOENT') fail(message)
      throw error
    }
  }
  // 下载页始终展示三张真实截图；缺图或占位图会阻止发布，即使尚未有 release。
  for (const file of APP_SCREENSHOT_FILES) {
    const bytes = await readOrFail(join(root, 'assets/app', file), `App screenshot ${file} is missing`)
    if (bytes.length < 4096) fail(`App screenshot ${file} is too small to be a real capture`)
    if (bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WEBP') fail(`App screenshot ${file} is not WebP`)
    if (!page.includes(`app/${file}`)) fail(`App page is missing screenshot ${file}`)
  }
  let raw
  try {
    raw = JSON.parse(await readFile(join(root, 'app/latest.json'), 'utf8'))
  } catch (error) {
    if (error?.code !== 'ENOENT') fail('App update manifest is not valid JSON')
    if (page.includes('/app/releases/')) fail('App page offers a download without a published release')
    return
  }
  const release = releaseObject(raw)
  if (!release) fail('App update manifest failed validation')
  const apk = await readOrFail(join(root, release.apkPath), 'App update manifest points at a missing APK')
  if (apk.length !== release.size) fail('App APK size does not match its manifest')
  if (createHash('sha256').update(apk).digest('hex') !== release.sha256) fail('App APK checksum does not match its manifest')
  assertApkMatchesRelease(join(root, release.apkPath), release)
  const codes = (await readdir(join(root, 'app/releases')).catch(() => [])).filter((name) => /^\d+$/.test(name))
  if (codes.length === 0) fail('App release directory is empty')
  if (codes.length > 3) fail('App release directory keeps more than three versions')
  if (Math.max(...codes.map(Number)) !== release.versionCode) fail('App latest manifest is not the newest retained release')
  for (const code of codes) {
    const manifest = await readOrFail(join(root, 'app/releases', code, 'release.json'), `App release ${code} is missing its manifest`)
    let kept = null
    try {
      kept = releaseObject(JSON.parse(manifest.toString('utf8')))
    } catch {
      kept = null
    }
    if (!kept || String(kept.versionCode) !== code) fail(`App release ${code} does not match its manifest`)
    const bytes = await readOrFail(join(root, kept.apkPath), `App release ${code} is missing its APK`)
    if (bytes.length !== kept.size) fail(`App release ${code} APK size does not match its manifest`)
    if (createHash('sha256').update(bytes).digest('hex') !== kept.sha256) fail(`App release ${code} APK checksum does not match its manifest`)
    assertApkMatchesRelease(join(root, kept.apkPath), kept)
  }
  if (JSON.stringify(JSON.parse((await readOrFail(join(root, 'app/latest.json'), 'App latest manifest is missing')).toString('utf8'))) !== JSON.stringify(release)) fail('App latest manifest is inconsistent')
  if (!page.includes(release.versionName)) fail('App page is missing the current version')
  if (!page.includes(String(release.size))) fail('App page is missing the APK size')
  if (!page.includes(`href="${release.apkPath}"`)) fail('App page download link does not match the manifest')
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
    const thumbFile = file.replace(/^media\//, 'thumbs/')
    let thumb
    try {
      thumb = await readFile(join(archive, thumbFile))
    } catch {
      fail('Thumbnail is missing')
    }
    if (thumb.toString('ascii', 0, 4) !== 'RIFF' || thumb.toString('ascii', 8, 12) !== 'WEBP') fail('Thumbnail is not WebP')
  }
  const mediaFiles = await readdir(join(archive, 'media'))
  if (mediaFiles.length !== references.size || mediaFiles.some(name => !references.has('media/' + name))) fail('Archive contains unreferenced media')
  const thumbReferences = new Set([...references].map(file => file.replace(/^media\//, 'thumbs/')))
  const thumbFiles = await readdir(join(archive, 'thumbs'))
  if (thumbFiles.length !== thumbReferences.size || thumbFiles.some(name => !thumbReferences.has('thumbs/' + name))) fail('Archive contains unreferenced thumbnails')
  let notices = null
  try {
    notices = await json(join(archive, 'notices.json'))
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error
  }
  if (notices) {
    if (notices.schema !== 1) fail('Unsupported notice archive schema')
    if (!Array.isArray(notices.versions)) fail('Notice archive versions must be an array')
    let previous = ''
    for (const item of notices.versions) {
      if (!/^[a-f0-9]{64}$/.test(item.hash)) fail('Invalid notice hash')
      if (!item.title.trim() || !item.message.trim()) fail('Empty notice content')
      if (!Number.isFinite(Date.parse(item.published_at)) || !Number.isFinite(Date.parse(item.first_seen_at))) fail('Invalid notice timestamp')
      if (item.first_seen_at < previous) fail('Notice archive is not ordered')
      previous = item.first_seen_at
      const noticeUrls = `${item.title} ${item.message}`.match(/https?:\/\/[^\s<>"'）)]+/gi) ?? []
    for (const url of noticeUrls) {
      if (normalizeNoticeLink(url) !== url) fail('Notice text contains an unapproved URL')
    }
    }
    if (notices.current && notices.current.hash !== notices.versions.at(-1)?.hash) fail('Notice current does not match the newest version')
    if (notices.current) {
      const escape = value => value.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch])
      const page = await readFile(join(root, 'index.html'), 'utf8')
      if (!page.includes(escape(notices.current.title))) fail('Rendered page is missing the current notice')
    }
  }
  const documents = [join(root, 'index.html'), join(root, 'api.html'), ...dates.map(date => join(root, 'archive', date + '.html'))]
  for (const file of documents) {
    const html = await readFile(file, 'utf8')
    if (/https?:\/\/(?:ok\.166\.net|raw\.githubusercontent\.com)/i.test(html) || /(?:authorization|admin_secret|access_token)\s*[:=]/i.test(html)) fail('Published HTML contains a private source or credential')
  }
  await checkAppRelease()
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
