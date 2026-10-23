export const APP_PACKAGE = 'com.nankki.skydaily'
export const APP_MIN_SDK = 28
export const APP_MANIFEST_PATH = '/app/latest.json'
export const APP_PAGE_PATH = '/app/'
// 下载页固定展示的三张真实截图，文件位于 assets/app/。
export const APP_SCREENSHOT_FILES = ['tasks.webp', 'meals.webp', 'settings.webp']

const SCHEMA_VERSION = 1
const MAX_VERSION_CODE = 2100000000
export const APP_MAX_APK_BYTES = 128 * 1024 * 1024
const MAX_NOTES = 12
const MAX_NOTE_LENGTH = 160
const APK_PATH_PATTERN = /^\/app\/releases\/(\d+)\/skydaily-[0-9A-Za-z.-]+\.apk$/
const VERSION_NAME_PATTERN = /^[0-9]+\.[0-9]+\.[0-9]+$/
const COMMIT_PATTERN = /^[a-f0-9]{40}$/
const SHA256_PATTERN = /^[a-f0-9]{64}$/

// 公开说明只保留一行纯文本：去掉链接、尖括号和 GitHub 字样，避免把内部记录带到页面与清单。
function cleanNote(line) {
  if (typeof line !== 'string') return ''
  const stripped = line
    .replace(/https?:\/\/\S+/gi, ' ')
    .replace(/www\.\S+/gi, ' ')
    .replace(/[<>]/g, ' ')
    .replace(/github(?:\.com)?/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return [...stripped].slice(0, MAX_NOTE_LENGTH).join('').trim()
}

/**
 * 清洗发布说明：去重、去掉空行、限制条数和长度。
 * @param {readonly string[]} lines
 * @returns {string[]}
 */
export function releaseNotes(lines) {
  const notes = []
  const seen = new Set()
  for (const line of lines ?? []) {
    const note = cleanNote(line)
    if (!note || seen.has(note)) continue
    seen.add(note)
    notes.push(note)
    if (notes.length === MAX_NOTES) break
  }
  return notes
}

/**
 * 校验并规范化发布对象；任何字段不合法都返回 null。
 * @param {unknown} value
 * @returns {object | null}
 */
export function releaseObject(value) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null
  const { schemaVersion, packageName, versionName, versionCode, commit, publishedAt, minSdk, size, sha256, apkPath, notes } = value
  if (schemaVersion !== SCHEMA_VERSION) return null
  if (packageName !== APP_PACKAGE) return null
  if (typeof versionName !== 'string' || !VERSION_NAME_PATTERN.test(versionName)) return null
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > MAX_VERSION_CODE) return null
  if (typeof commit !== 'string' || !COMMIT_PATTERN.test(commit)) return null
  if (typeof publishedAt !== 'string' || !publishedAt.endsWith('Z')) return null
  const publishedTime = Date.parse(publishedAt)
  if (!Number.isFinite(publishedTime) || new Date(publishedTime).toISOString() !== publishedAt) return null
  if (!Number.isInteger(minSdk) || minSdk < 1 || minSdk > 1000) return null
  if (!Number.isInteger(size) || size < 1 || size > APP_MAX_APK_BYTES) return null
  if (typeof sha256 !== 'string' || !SHA256_PATTERN.test(sha256)) return null
  if (typeof apkPath !== 'string') return null
  const matched = APK_PATH_PATTERN.exec(apkPath)
  if (!matched || Number(matched[1]) !== versionCode) return null
  if (apkPath !== `/app/releases/${versionCode}/${apkFileFor({ versionName })}`) return null
  if (!Array.isArray(notes) || notes.length > MAX_NOTES) return null
  const cleaned = releaseNotes(notes)
  if (cleaned.length !== notes.length || cleaned.some((note, index) => note !== notes[index])) return null
  return {
    schemaVersion: SCHEMA_VERSION,
    packageName: APP_PACKAGE,
    versionName,
    versionCode,
    commit,
    publishedAt,
    minSdk,
    size,
    sha256,
    apkPath,
    notes: cleaned,
  }
}

const APP_BUILD_INPUTS = new Set([
  'app/build.gradle.kts',
  'app/settings.gradle.kts',
  'app/gradle.properties',
  'app/gradlew',
  'app/gradlew.bat',
  'app/app/build.gradle.kts',
  'app/app/proguard-rules.pro',
])

/**
 * 该路径是否属于会影响安卓安装包的改动。
 * @param {string} path
 * @returns {boolean}
 */
export function isEligibleAppPath(path) {
  if (typeof path !== 'string') return false
  const normalized = path.trim().replaceAll('\\', '/')
  if (!normalized || normalized.startsWith('/') || normalized.split('/').includes('..')) return false
  if (normalized === 'src/link-policy.json' || normalized === 'src/link-policy.mjs') return true
  if (normalized.toLowerCase().endsWith('.md')) return false
  return APP_BUILD_INPUTS.has(normalized)
    || normalized.startsWith('app/gradle/')
    || normalized.startsWith('app/app/src/main/')
    || normalized.startsWith('app/app/src/release/')
}

/**
 * @param {{ versionName: string }} release
 * @returns {string}
 */
export function apkFileFor(release) {
  return `skydaily-${release.versionName}.apk`
}

/**
 * @param {number} versionCode
 * @returns {string}
 */
export function releaseDir(versionCode) {
  return `app/releases/${versionCode}`
}
