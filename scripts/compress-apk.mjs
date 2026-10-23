#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdtempSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { APP_CERT_SHA256, androidTools, inspectApk } from '../src/apk-inspect.mjs'

const USAGE = '用法：compress-apk.mjs <apk> [输出 apk]（需 ANDROID_KEYSTORE_* 四项环境变量）'
const SIGNING_ENV = [
  'ANDROID_KEYSTORE_PATH',
  'ANDROID_KEYSTORE_PASSWORD',
  'ANDROID_KEY_ALIAS',
  'ANDROID_KEY_PASSWORD',
]

function fail(message) {
  throw new Error(message)
}

function run(tool, args) {
  try {
    return execFileSync(tool, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      maxBuffer: 8 * 1024 * 1024,
    })
  } catch (error) {
    const detail = `${error.stdout ?? ''}\n${error.stderr ?? ''}`
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .slice(0, 3)
      .join(' ')
    throw new Error(detail || `构建工具 ${tool.split(/[\\/]/).pop()} 执行失败`)
  }
}

function main() {
  const args = process.argv.slice(2)
  if (args.length < 1 || args.length > 2) fail(USAGE)
  const input = resolve(args[0])
  const output = resolve(args[1] ?? args[0])
  if (!existsSync(input)) fail(`找不到 APK：${input}`)
  const before = statSync(input).size
  for (const name of SIGNING_ENV) {
    if (!process.env[name]) fail(`缺少环境变量 ${name}`)
  }

  const { apksigner } = androidTools()
  const zipalign = join(dirname(apksigner), process.platform === 'win32' ? 'zipalign.exe' : 'zipalign')
  if (!existsSync(zipalign)) fail(`找不到 zipalign：${zipalign}`)

  const work = mkdtempSync(join(tmpdir(), 'skydaily-apk-'))
  try {
    const aligned = join(work, 'aligned.apk')
    const signed = join(work, 'signed.apk')
    // Zopfli 重压所有已压缩条目，-P 16 让未压缩 .so 兼容 16 KiB 页设备，4 保持 stored 条目 4 字节对齐。
    run(zipalign, ['-f', '-z', '-P', '16', '4', input, aligned])
    run(apksigner, [
      'sign',
      '--ks', process.env.ANDROID_KEYSTORE_PATH,
      '--ks-key-alias', process.env.ANDROID_KEY_ALIAS,
      '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD',
      '--key-pass', 'env:ANDROID_KEY_PASSWORD',
      // 与既有 0.2.0 产物一致：minSdk 28 下只需 v2，省去 v1 的 META-INF 签名文件。
      '--v1-signing-enabled', 'false',
      '--v2-signing-enabled', 'true',
      '--v3-signing-enabled', 'false',
      '--out', signed,
      aligned,
    ])
    const signedInfo = inspectApk(signed)
    if (signedInfo.certificateSha256 !== APP_CERT_SHA256) fail('签名证书与既有安装身份不一致')
    run(zipalign, ['-c', '-P', '16', '4', signed])

    copyFileSync(signed, output)
    const after = statSync(output).size
    const saved = before - after
    console.log(`APK ${before} -> ${after} 字节（节省 ${saved}，${((saved / before) * 100).toFixed(1)}%）`)
  } finally {
    rmSync(work, { recursive: true, force: true })
  }
}

try {
  main()
} catch (error) {
  console.error(`compress-apk: ${error.message}`)
  process.exit(1)
}
