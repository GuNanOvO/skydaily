#!/usr/bin/env node
import { createHash } from 'node:crypto'
import { cp, mkdir, open, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises'
import { basename, dirname, join, resolve } from 'node:path'
import { APP_MAX_APK_BYTES, apkFileFor, releaseObject } from '../src/app-release.mjs'
import { assertApkMatchesRelease } from '../src/apk-inspect.mjs'

const DEFAULT_KEEP = 3
const MAX_KEEP = 10
const MAX_SCAN = 10
const MAX_FILE_BYTES = APP_MAX_APK_BYTES
const MAX_MANIFEST_BYTES = 64 * 1024
const TAG_PATTERN = /^app-v[0-9]+\.[0-9]+\.[0-9]+$/
const USAGE = '用法：prepare-app-release.mjs --stage <dir> [--tag <app-vX.Y.Z>] [--keep 3] | --stage <dir> --integrate <out> | --stage <dir> --local <apk> --local-manifest <json>'

function fail(message) {
  throw new Error(message)
}

function parseOptions(argv) {
  const options = new Map()
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (!token.startsWith('--')) fail(`无法识别的参数：${token}`)
    const eq = token.indexOf('=')
    if (eq !== -1) {
      options.set(token.slice(2, eq), token.slice(eq + 1))
      continue
    }
    const value = argv[index + 1]
    if (value === undefined || value.startsWith('--')) fail(`缺少参数值：${token}`)
    options.set(token.slice(2), value)
    index += 1
  }
  return options
}

function parseManifest(text, label) {
  let value
  try {
    value = JSON.parse(text)
  } catch {
    fail(`${label} 不是有效的 JSON`)
  }
  const release = releaseObject(value)
  if (!release) fail(`${label} 未通过发布清单校验`)
  return release
}

async function readVerifiedApk(path) {
  const size = await stat(path)
  if (size.size > MAX_FILE_BYTES) fail(`${basename(path)} 超出 ${MAX_FILE_BYTES} 字节上限`)
  const bytes = await readFile(path)
  if (bytes.length !== size.size) fail(`${basename(path)} 在读取过程中发生变化`)
  return bytes
}

// 大小和哈希只能证明文件未变；包名、版本、minSdk 与签名证书必须从 APK 本体读取。
async function verifyApk(path, release) {
  const bytes = await readVerifiedApk(path)
  if (bytes.length !== release.size) fail(`${release.versionName} 的安装包大小与清单不符`)
  if (createHash('sha256').update(bytes).digest('hex') !== release.sha256) fail(`${release.versionName} 的安装包校验值与清单不符`)
  return assertApkMatchesRelease(path, release)
}

// 暂存目录整体替换，校验失败时不会留下半份发布文件。
async function writeStage(stageDir, items, latestTag) {
  const work = `${stageDir}.partial`
  await mkdir(dirname(stageDir), { recursive: true })
  await rm(work, { recursive: true, force: true })
  await mkdir(join(work, 'app', 'releases'), { recursive: true })
  const records = []
  for (const item of items) {
    const relativeDir = `app/releases/${item.manifest.versionCode}`
    const dir = join(work, relativeDir)
    await mkdir(dir, { recursive: true })
    const apk = join(relativeDir, apkFileFor(item.manifest))
    await cp(item.apkPath, join(work, apk))
    const manifest = join(relativeDir, 'release.json')
    await writeFile(join(work, manifest), `${JSON.stringify(item.manifest, null, 2)}\n`)
    records.push({ tag: item.tag, versionCode: item.manifest.versionCode, versionName: item.manifest.versionName, apk, manifest })
  }
  let latest = null
  const current = items.find((item) => item.tag === latestTag)
  if (current) {
    latest = 'app/latest.json'
    await writeFile(join(work, latest), `${JSON.stringify(current.manifest, null, 2)}\n`)
  }
  // 清单中的路径相对于该文件所在目录，避免引用重命名前的 .partial 路径。
  const summary = { schemaVersion: 1, stagedAt: new Date().toISOString(), tag: current?.tag ?? null, latest, releases: records }
  await writeFile(join(work, 'release.json'), `${JSON.stringify(summary, null, 2)}\n`)
  await rm(stageDir, { recursive: true, force: true })
  await rename(work, stageDir)
  return summary
}

async function integrateRelease(stageDir, outDir) {
  try {
    await stat(join(outDir, 'index.html'))
  } catch {
    fail(`站点输出目录缺少 index.html：${outDir}`)
  }
  const summaryPath = join(stageDir, 'release.json')
  const summary = JSON.parse(await readFile(summaryPath, 'utf8'))
  if (!Array.isArray(summary.releases)) fail('暂存信息缺少发布列表')
  const stageRoot = dirname(resolve(summaryPath))
  const latestPath = summary.latest ? resolve(stageRoot, summary.latest) : null
  const latest = latestPath ? parseManifest(await readFile(latestPath, 'utf8'), '暂存的最新清单') : null
  const verified = []
  const codes = new Set()
  for (const item of summary.releases) {
    const manifestPath = resolve(stageRoot, item.manifest)
    const apkPath = resolve(stageRoot, item.apk)
    const manifest = parseManifest(await readFile(manifestPath, 'utf8'), `暂存清单 ${item.versionCode}`)
    if (manifest.versionCode !== item.versionCode) fail('暂存清单编号与目录不一致')
    if (codes.has(manifest.versionCode)) fail(`暂存发布中存在重复版本号 ${manifest.versionCode}`)
    codes.add(manifest.versionCode)
    if (basename(apkPath) !== apkFileFor(manifest)) fail('暂存安装包文件名与清单不一致')
    await verifyApk(apkPath, manifest)
    verified.push({ manifest, apk: apkPath, manifestPath })
  }
  if (latest) {
    if (!verified.some((item) => item.manifest.versionCode === latest.versionCode)) fail('最新清单不在待复制的发布列表中')
    if (verified.some((item) => item.manifest.versionCode > latest.versionCode)) fail('最新清单不是编号最大的发布版本')
    if (!latestPath) fail('最新清单缺少文件路径')
  }
  const appDir = join(outDir, 'app')
  await mkdir(join(appDir, 'releases'), { recursive: true })
  for (const item of verified) {
    const dir = join(appDir, 'releases', String(item.manifest.versionCode))
    await mkdir(dir, { recursive: true })
    await cp(item.apk, join(dir, apkFileFor(item.manifest)))
    await cp(item.manifestPath, join(dir, 'release.json'))
  }
  // 清单最后写入，保证它不会指向尚未复制的安装包。
  if (latest && latestPath) await cp(latestPath, join(appDir, 'latest.json'))
  else await rm(join(appDir, 'latest.json'), { force: true })
  const kept = new Set(verified.map((item) => String(item.manifest.versionCode)))
  const existing = await readdir(join(appDir, 'releases')).catch(() => [])
  for (const name of existing) {
    if (!kept.has(name)) await rm(join(appDir, 'releases', name), { recursive: true, force: true })
  }
  return verified.length
}

function githubHeaders(token, accept) {
  return {
    accept,
    authorization: `Bearer ${token}`,
    'x-github-api-version': '2022-11-28',
    'user-agent': 'skydaily-release',
  }
}

async function downloadAsset(asset, target, token) {
  if (typeof asset.size === 'number' && asset.size > MAX_FILE_BYTES) fail(`${asset.name} 超出大小上限`)
  const response = await fetch(asset.url, { headers: githubHeaders(token, 'application/octet-stream') })
  if (!response.ok) fail(`下载 ${asset.name} 失败：HTTP ${response.status}`)
  const declared = Number(response.headers.get('content-length') ?? '')
  if (Number.isFinite(declared) && declared > MAX_FILE_BYTES) fail(`${asset.name} 超出大小上限`)
  const handle = await open(target, 'w')
  let total = 0
  try {
    for await (const chunk of response.body ?? []) {
      total += chunk.length
      if (total > MAX_FILE_BYTES) fail(`${asset.name} 超出大小上限`)
      await handle.write(chunk)
    }
  } finally {
    await handle.close()
  }
  if (typeof asset.size === 'number' && total !== asset.size) fail(`${asset.name} 下载大小不符`)
  return total
}

// 清单解析失败返回 null，由调用方决定是否跳过；网络错误直接失败。
async function readGithubManifest(release, token) {
  const asset = (release.assets ?? []).find((item) => item.name === 'app-release.json')
  if (!asset) return null
  if (typeof asset.size === 'number' && asset.size > MAX_MANIFEST_BYTES) fail(`${release.tag_name} 的清单超出大小上限`)
  const response = await fetch(asset.url, { headers: githubHeaders(token, 'application/octet-stream') })
  if (!response.ok) fail(`读取 ${release.tag_name} 的清单失败：HTTP ${response.status}`)
  const declared = Number(response.headers.get('content-length') ?? '')
  if (Number.isFinite(declared) && declared > MAX_MANIFEST_BYTES) fail(`${release.tag_name} 的清单超出大小上限`)
  const text = await response.text()
  if (text.length > MAX_MANIFEST_BYTES) fail(`${release.tag_name} 的清单超出大小上限`)
  let parsed
  try {
    parsed = releaseObject(JSON.parse(text))
  } catch {
    return null
  }
  if (!parsed || release.tag_name !== `app-v${parsed.versionName}`) return null
  return parsed
}

async function stageFromGithub(stageDir, options) {
  const token = process.env.GH_TOKEN ?? process.env.GITHUB_TOKEN
  if (!token) fail('缺少 GH_TOKEN')
  const repo = options.get('repo') ?? process.env.GITHUB_REPOSITORY
  if (!repo || !/^[\w.-]+\/[\w.-]+$/.test(repo)) fail('缺少 GITHUB_REPOSITORY 或 --repo')
  const requestedTag = options.get('tag')
  if (requestedTag && !TAG_PATTERN.test(requestedTag)) fail(`--tag 格式无效：${requestedTag}`)

  const response = await fetch(`https://api.github.com/repos/${repo}/releases?per_page=100`, { headers: githubHeaders(token, 'application/vnd.github+json') })
  if (!response.ok) fail(`读取 GitHub 发布列表失败：HTTP ${response.status}`)
  const releases = await response.json()
  if (!Array.isArray(releases)) fail('GitHub 发布列表格式异常')

  const published = releases
    .filter((item) => item && !item.draft && TAG_PATTERN.test(item.tag_name ?? ''))
    .sort((a, b) => Date.parse(b.published_at ?? b.created_at) - Date.parse(a.published_at ?? a.created_at))
  if (requestedTag && !published.some((item) => item.tag_name === requestedTag)) fail(`未找到已发布的 ${requestedTag}`)
  if (published.length === 0) {
    await writeStage(stageDir, [], null)
    console.log('App 发布：尚未发布任何版本')
    return null
  }

  const keep = options.has('keep') ? Number(options.get('keep')) : DEFAULT_KEEP
  if (!Number.isInteger(keep) || keep < 1 || keep > MAX_KEEP) fail(`--keep 需为 1 到 ${MAX_KEEP} 的整数`)

  // 在本次串行发布内部重新读取当前最新发布；dispatch 标签只用于补查，不能把 latest 回退。
  const items = []
  const invalid = []
  const scanLimit = Math.min(published.length, MAX_SCAN)
  for (const release of published.slice(0, scanLimit)) {
    const manifest = await readGithubManifest(release, token)
    if (!manifest) {
      if (release.tag_name === published[0].tag_name) fail(`最新发布的清单无效：${release.tag_name}`)
      invalid.push(release.tag_name)
      continue
    }
    items.push({ tag: release.tag_name, raw: release, manifest })
    if (items.length >= keep) break
  }
  if (requestedTag && !items.some((item) => item.tag === requestedTag)) {
    const extra = published.find((item) => item.tag_name === requestedTag)
    if (extra) {
      const manifest = await readGithubManifest(extra, token)
      if (!manifest) fail(`${requestedTag} 的发布清单无效`)
      items.push({ tag: extra.tag_name, raw: extra, manifest })
    }
  }
  if (invalid.length > 0) console.warn(`跳过清单无效的发布：${invalid.join(', ')}`)
  if (items.length === 0) fail('没有可用的 App 发布清单')
  const codes = new Set()
  for (const item of items) {
    if (codes.has(item.manifest.versionCode)) fail(`发布列表中存在重复版本号 ${item.manifest.versionCode}`)
    codes.add(item.manifest.versionCode)
  }
  items.sort((a, b) => b.manifest.versionCode - a.manifest.versionCode)
  const kept = items.slice(0, keep)

  const downloadDir = `${stageDir}.downloads`
  await rm(downloadDir, { recursive: true, force: true })
  await mkdir(downloadDir, { recursive: true })
  try {
    for (const item of kept) {
      const name = apkFileFor(item.manifest)
      const asset = (item.raw.assets ?? []).find((entry) => entry.name === name)
      if (!asset) fail(`${item.tag} 缺少安装包 ${name}`)
      item.apkPath = join(downloadDir, name)
      await downloadAsset(asset, item.apkPath, token)
      await verifyApk(item.apkPath, item.manifest)
    }
    const summary = await writeStage(stageDir, kept, kept[0].tag)
    console.log(`App 发布：${summary.releases.map((item) => item.versionName).join(', ')} → ${stageDir}`)
    return summary
  } finally {
    await rm(downloadDir, { recursive: true, force: true })
  }
}

async function main() {
  const options = parseOptions(process.argv.slice(2))
  const stage = options.get('stage')
  if (!stage) fail(`缺少 --stage\n${USAGE}`)
  const stageDir = resolve(stage)
  const integrateDir = options.get('integrate')
  if (integrateDir) {
    const count = await integrateRelease(stageDir, resolve(integrateDir))
    console.log(`已将 ${count} 个 App 发布复制到 ${integrateDir}/app`)
    return
  }
  const localApk = options.get('local')
  const localManifest = options.get('local-manifest')
  if (localApk || localManifest) {
    if (!localApk || !localManifest) fail('--local 与 --local-manifest 需要同时提供')
    const manifest = parseManifest(await readFile(resolve(localManifest), 'utf8'), '本地清单')
    await verifyApk(resolve(localApk), manifest)
    const tag = `app-v${manifest.versionName}`
    await writeStage(stageDir, [{ tag, apkPath: resolve(localApk), manifest }], tag)
    console.log(`App 发布（本地）：${manifest.versionName} → ${stageDir}`)
    return
  }
  await stageFromGithub(stageDir, options)
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : 'App 发布暂存失败')
  process.exitCode = 1
})
