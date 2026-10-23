#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { isEligibleAppPath, releaseNotes } from '../src/app-release.mjs'

// 与安卓 versionCode 的整数上限保持一致，超出后无法发布。
const MAX_VERSION_CODE = 2100000000
const LEVEL_RANK = { patch: 1, minor: 2, major: 3 }
const USAGE = '用法：app-version.mjs --commits <file|-> --paths <file|-> --previous-code <n> --previous-name <name> --count <n> [--same-commit <sha> --published-commit <sha> --published-name <name> --published-code <n>]'

function fail(message) {
  console.error(message)
  console.error(USAGE)
  process.exit(2)
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

function readText(path) {
  return path === '-' ? readFileSync(0, 'utf8') : readFileSync(path, 'utf8')
}

function parseNumber(options, name, fallback) {
  const raw = options.get(name)
  if (raw === undefined) return fallback
  if (!/^\d+$/.test(raw)) fail(`--${name} 必须是非负整数`)
  const value = Number(raw)
  if (!Number.isSafeInteger(value)) fail(`--${name} 超出可用范围`)
  return value
}

// 每行 `sha<TAB>subject<TAB>body`；body 里的换行会以续行形式出现，并接到上一条提交。
function readCommits(path) {
  if (!path) fail('缺少 --commits')
  const commits = []
  for (const line of readText(path).split('\n')) {
    const fields = line.split('\t')
    if (fields.length >= 2 && /^[0-9a-f]{40}$/i.test(fields[0])) {
      commits.push({ sha: fields[0].toLowerCase(), subject: fields[1].trim(), body: fields.slice(2).join('\t') })
    } else if (commits.length > 0 && line.trim()) {
      commits.at(-1).body += `\n${line}`
    }
  }
  return commits
}

function readPaths(path) {
  const paths = new Map()
  if (!path) return paths
  for (const line of readText(path).split('\n')) {
    const [sha, ...rest] = line.split('\t')
    const file = rest.join('\t').trim()
    if (!/^[0-9a-f]{40}$/i.test(sha ?? '') || !file) continue
    const key = sha.toLowerCase()
    if (!paths.has(key)) paths.set(key, new Set())
    paths.get(key).add(file)
  }
  return paths
}

// 破坏性改动优先于 feat，其次一律按补丁发布。
function commitLevel(commit) {
  if (/^[a-z]+(?:\([^)]*\))?!:/.test(commit.subject) || /(?:^|\n)BREAKING CHANGE:/.test(commit.body)) return 'major'
  if (/^feat(?:\([^)]*\))?!?:/.test(commit.subject)) return 'minor'
  return 'patch'
}

function bumpVersion(name, level) {
  const matched = /^(\d+)\.(\d+)\.(\d+)$/.exec(name)
  if (!matched) return null
  const major = Number(matched[1])
  const minor = Number(matched[2])
  const patch = Number(matched[3])
  if (level === 'major') return `${major + 1}.0.0`
  if (level === 'minor') return `${major}.${minor + 1}.0`
  return `${major}.${minor}.${patch + 1}`
}

const options = parseOptions(process.argv.slice(2))
const commits = readCommits(options.get('commits'))
const paths = readPaths(options.get('paths'))
const eligible = commits.filter((commit) => [...(paths.get(commit.sha) ?? [])].some(isEligibleAppPath))

const sameCommit = options.get('same-commit')?.toLowerCase()
const publishedCommit = options.get('published-commit')?.toLowerCase()
const publishedName = options.get('published-name') ?? ''
const publishedCode = parseNumber(options, 'published-code', 0)
if (publishedCommit) {
  if (!/^\d+\.\d+\.\d+$/.test(publishedName)) fail('缺少有效的已发布版本名')
  if (!Number.isInteger(publishedCode) || publishedCode < 1 || publishedCode > MAX_VERSION_CODE) fail('缺少有效的已发布版本号')
}
if (sameCommit && publishedCommit && sameCommit === publishedCommit) {
  console.log(JSON.stringify({ release: true, reuse: true, versionName: publishedName, versionCode: publishedCode, level: null, commits: eligible.length }))
  process.exit(0)
}

if (eligible.length === 0) {
  console.log(JSON.stringify({ release: false }))
  process.exit(0)
}

// 不同提交也必须从上一份已发布版本递增，不能因为缺少 --previous-* 而退回 0.1.0。
const explicitCode = parseNumber(options, 'previous-code', 0)
const previousCode = explicitCode > 0 ? explicitCode : publishedCode
const count = parseNumber(options, 'count', 0)
const previousName = options.get('previous-name') || publishedName
const level = eligible.reduce((highest, commit) => {
  const current = commitLevel(commit)
  return LEVEL_RANK[current] > LEVEL_RANK[highest] ? current : highest
}, 'patch')

let versionName
if (previousCode === 0) {
  versionName = '0.1.0'
} else {
  if (!/^\d+\.\d+\.\d+$/.test(previousName)) fail('缺少已发布版本名，无法递增')
  versionName = bumpVersion(previousName, level)
  if (!versionName) fail('无法从已发布版本名递增')
}

const versionCode = Math.max(count, previousCode + 1, 1)
if (versionCode > MAX_VERSION_CODE) {
  console.error(`版本号 ${versionCode} 超过安卓上限 ${MAX_VERSION_CODE}`)
  process.exit(2)
}

console.log(JSON.stringify({
  release: true,
  versionName,
  versionCode,
  level,
  commits: eligible.length,
  notes: releaseNotes(eligible.map((commit) => commit.subject)),
}))
