#!/usr/bin/env node
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { assertApkMatchesRelease } from '../src/apk-inspect.mjs'
import { releaseObject } from '../src/app-release.mjs'

const USAGE = '用法：verify-app-release.mjs --apk <path> --manifest <path> [--commit <sha>] [--name <versionName>] [--code <versionCode>]'

function fail(message) {
  console.error(message)
  console.error(USAGE)
  process.exitCode = 1
}

function parseOptions(argv) {
  const options = new Map()
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (!token.startsWith('--')) {
      fail(`无法识别的参数：${token}`)
      return null
    }
    const value = argv[index + 1]
    if (value === undefined || value.startsWith('--')) {
      fail(`缺少参数值：${token}`)
      return null
    }
    options.set(token.slice(2), value)
    index += 1
  }
  return options
}

const options = parseOptions(process.argv.slice(2))
if (options) {
  const apkPath = options.get('apk')
  const manifestPath = options.get('manifest')
  if (!apkPath || !manifestPath) fail(`缺少 --apk 或 --manifest\n${USAGE}`)
  else {
    try {
      const release = releaseObject(JSON.parse(readFileSync(manifestPath, 'utf8')))
      if (!release) throw new Error('发布清单未通过校验')
      const expectedCommit = options.get('commit')?.toLowerCase()
      if (expectedCommit && release.commit !== expectedCommit) throw new Error(`清单提交为 ${release.commit}，与期望的 ${expectedCommit} 不符`)
      const expectedName = options.get('name')
      if (expectedName && release.versionName !== expectedName) throw new Error(`清单版本名为 ${release.versionName}，与期望的 ${expectedName} 不符`)
      const expectedCode = options.get('code')
      if (expectedCode && String(release.versionCode) !== expectedCode) throw new Error(`清单版本号为 ${release.versionCode}，与期望的 ${expectedCode} 不符`)
      const apk = readFileSync(apkPath)
      if (apk.length !== release.size) throw new Error('安装包大小与清单不符')
      if (createHash('sha256').update(apk).digest('hex') !== release.sha256) throw new Error('安装包校验值与清单不符')
      const info = assertApkMatchesRelease(apkPath, release)
      console.log(JSON.stringify({ ...info, size: apk.length, sha256: release.sha256 }))
    } catch (error) {
      fail(error instanceof Error ? error.message : '安装包校验失败')
    }
  }
}
