import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { APP_PACKAGE } from './app-release.mjs'

// APK 签名证书的公开身份；用于确认产物与当前已安装版本可以覆盖升级。
export const APP_CERT_SHA256 = '9463bb1d8ad40eda6f358716449fd6646c1dac28fea817ce425b579b8f05820a'

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const TOOL_SUFFIX = process.platform === 'win32' ? '.exe' : ''
const CERT_PATTERN = /certificate SHA-256 digest:\s*([0-9a-fA-F]{64})/
const SCHEME_PATTERN = /^V(\d) Signer:/m

function fail(message) {
  throw new Error(message)
}

/** 从 `app/local.properties` 读取本机 SDK 路径；只用于查找构建工具。 */
function localSdkDir() {
  const file = join(REPO_ROOT, 'app', 'local.properties')
  if (!existsSync(file)) return null
  const match = /^\s*sdk\.dir\s*=\s*(.+)$/m.exec(readFileSync(file, 'utf8'))
  return match ? match[1].trim() : null
}

function versionParts(name) {
  return name.split('.').map((part) => Number.parseInt(part, 10) || 0)
}

function compareVersions(left, right) {
  const a = versionParts(left)
  const b = versionParts(right)
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    const delta = (a[index] ?? 0) - (b[index] ?? 0)
    if (delta !== 0) return delta
  }
  return 0
}

/**
 * 定位 Android SDK 构建工具。发布校验必须使用 aapt2 与 apksigner：
 * 只有它们能证明签名确实覆盖了安装包内容，单纯读取证书不等于验签。
 * @returns {{ aapt2: string, apksigner: string }}
 */
export function androidTools() {
  const explicit = process.env.ANDROID_BUILD_TOOLS
  const buildToolsRoots = []
  if (explicit) buildToolsRoots.push(explicit)
  for (const root of [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT, localSdkDir()]) {
    if (root) buildToolsRoots.push(join(root, 'build-tools'))
  }
  buildToolsRoots.push(join(homedir(), 'Android', 'Sdk', 'build-tools'))
  buildToolsRoots.push(join(homedir(), 'Library', 'Android', 'sdk', 'build-tools'))

  for (const root of buildToolsRoots) {
    if (!existsSync(root)) continue
    const directories = readdirSync(root, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort((a, b) => compareVersions(b, a))
    for (const directory of directories) {
      const aapt2 = join(root, directory, `aapt2${TOOL_SUFFIX}`)
      const apksigner = join(root, directory, process.platform === 'win32' ? 'apksigner.bat' : 'apksigner')
      if (existsSync(aapt2) && existsSync(apksigner)) return { aapt2, apksigner }
    }
  }
  fail('找不到 Android 构建工具：请设置 ANDROID_HOME 或 ANDROID_BUILD_TOOLS')
}

function run(tool, args) {
  try {
    return execFileSync(tool, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 8 * 1024 * 1024 })
  } catch (error) {
    const detail = `${error.stdout ?? ''}\n${error.stderr ?? ''}`
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.startsWith('ERROR') || line.startsWith('DOES NOT VERIFY'))
      .slice(0, 2)
      .join(' ')
    throw new Error(detail || `构建工具 ${tool.split(/[\\/]/).pop()} 执行失败`)
  }
}

/**
 * 用官方工具核对 APK 本体：清单字段来自 aapt2，签名真实性来自 apksigner。
 * apksigner 校验失败（内容被改动、签名缺失）时会直接报错，不会返回证书信息。
 * @param {string} apkPath
 * @returns {{ packageName: string, versionCode: number, versionName: string, minSdk: number, certificateSha256: string, signingScheme: string }}
 */
export function inspectApk(apkPath) {
  if (typeof apkPath !== 'string' || !existsSync(apkPath)) fail(`安装包不存在：${apkPath}`)
  const tools = androidTools()
  const badging = run(tools.aapt2, ['dump', 'badging', apkPath])
  const packageLine = badging.split('\n').find((line) => line.startsWith('package: '))
  if (!packageLine) fail('安装包缺少包信息')
  const read = (key) => new RegExp(`${key}='([^']*)'`).exec(packageLine)?.[1] ?? null
  const packageName = read('name')
  const versionName = read('versionName')
  const versionCode = Number(read('versionCode'))
  const minSdk = Number(/minSdkVersion:'(\d+)'/.exec(badging)?.[1])
  if (!packageName || !versionName) fail('安装包缺少包名或版本名')
  if (!Number.isInteger(versionCode) || versionCode < 1) fail('安装包的 versionCode 无效')
  if (!Number.isInteger(minSdk) || minSdk < 1) fail('安装包的 minSdkVersion 无效')

  const certificates = run(tools.apksigner, ['verify', '--print-certs', apkPath])
  const certificateSha256 = CERT_PATTERN.exec(certificates)?.[1]?.toLowerCase()
  if (!certificateSha256) fail('安装包没有可用的签名证书')
  const signingScheme = SCHEME_PATTERN.exec(certificates)?.[1] ? `v${SCHEME_PATTERN.exec(certificates)[1]}` : 'v1'
  return { packageName, versionCode, versionName, minSdk, certificateSha256, signingScheme }
}

/**
 * 确认 APK 本体与发布清单、预期包名/证书一致。
 * @param {string} apkPath
 * @param {{ versionName: string, versionCode: number, minSdk: number }} release
 * @param {string} [expectedCertificate]
 * @returns {ReturnType<typeof inspectApk>}
 */
export function assertApkMatchesRelease(apkPath, release, expectedCertificate = APP_CERT_SHA256) {
  const info = inspectApk(apkPath)
  if (info.packageName !== APP_PACKAGE) fail(`APK 包名为 ${info.packageName}，与期望的 ${APP_PACKAGE} 不符`)
  if (info.versionCode !== release.versionCode) fail(`APK versionCode 为 ${info.versionCode}，与清单 ${release.versionCode} 不符`)
  if (info.versionName !== release.versionName) fail(`APK versionName 为 ${info.versionName}，与清单 ${release.versionName} 不符`)
  if (info.minSdk !== release.minSdk) fail(`APK minSdk 为 ${info.minSdk}，与清单 ${release.minSdk} 不符`)
  if (info.certificateSha256 !== expectedCertificate) fail(`APK 签名证书为 ${info.certificateSha256}，与既有安装身份不一致`)
  return info
}
