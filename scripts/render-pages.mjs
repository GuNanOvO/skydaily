import { dateNavigation } from './date-navigation.mjs'
import { readFileSync } from 'node:fs'
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { basename, dirname, join, resolve } from 'node:path'
import QRCode from 'qrcode'
import { normalizeNoticeLink, normalizeSeasonGuideLink, seasonGuidePolicy } from '../src/link-policy.mjs'
import { APP_PAGE_PATH, APP_SCREENSHOT_FILES, releaseObject } from '../src/app-release.mjs'
import { assertApkMatchesRelease } from '../src/apk-inspect.mjs'


const SEASON_GUIDE_URL = seasonGuidePolicy.fallback
const SITE_URL = (process.env.SITE_URL ?? 'https://skydaily.nankki.com').replace(/\/$/, '')
const ASSET_VERSION = createHash('sha256').update(readFileSync(new URL('../assets/notebook.css', import.meta.url))).update(readFileSync(new URL('../assets/notebook.js', import.meta.url))).update(readFileSync(new URL('../assets/theme.js', import.meta.url))).digest('hex').slice(0, 12)
const ICON_VERSION = createHash('sha256').update(readFileSync(new URL('../assets/favicon.svg', import.meta.url))).digest('hex').slice(0, 12)
const SHARE_QR = await QRCode.toString(SITE_URL + '/', { type: 'svg', margin: 0 })
// 内联 SVG 不需要 XML 声明和命名空间，去掉后页面里不会出现 http:// 字样。
const APP_QR = (await QRCode.toString(SITE_URL + APP_PAGE_PATH, { type: 'svg', margin: 0 }))
  .replace(/^<\?xml[^>]*\?>\s*/, '')
  .replace(' xmlns="http://www.w3.org/2000/svg"', '')

function faviconLinks(assets = 'assets/') {
  return `<link rel="icon" href="${assets}favicon.ico?v=${ICON_VERSION}" sizes="16x16 32x32 48x48"><link rel="icon" href="${assets}favicon.svg?v=${ICON_VERSION}" type="image/svg+xml" sizes="any">`
}

export function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch])
}

function beijingTime(iso) {
  return new Date(new Date(iso).getTime() + 8 * 3600_000).toISOString().slice(11, 16)
}

function linkedImage(src, alt) {
  const url = escapeHtml(src)
  const image = `<img src="${url}" loading="lazy" decoding="async" alt="${escapeHtml(alt)}">`
  if (!/^(?:https?:\/\/|(?:\.\.\/)?history\/media\/)/i.test(src)) return image
  return `<a class="image-link" href="${url}" target="_blank" rel="noopener noreferrer" aria-label="查看${escapeHtml(alt)}原图">${image}</a>`
}

function renderText(text) {
  const out = []
  let list = []
  const flush = () => {
    if (list.length > 0) {
      out.push(`<ol>${list.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ol>`)
      list = []
    }
  }
  for (const line of String(text).split('\n')) {
    const matched = /^\s*\d+[.、]\s*(.+)$/.exec(line)
    if (matched) list.push(matched[1])
    else {
      flush()
      if (line.trim()) out.push(`<p>${escapeHtml(line)}</p>`)
    }
  }
  flush()
  return out.join('')
}

function renderDetailText(d) {
  const sections = d.sections
    .filter((s) => s.text && s.text.trim())
    .map((s) => `<h4>${escapeHtml(s.label)}</h4>${renderText(s.text)}`)
    .join('')
  return sections || (d.text ? renderText(d.text) : '')
}

function renderDetailMedia(d) {
  const images = d.images.map((src) => linkedImage(src, '教程图片')).join('')
  const videos = d.videos
    .map((src) => `<video controls preload="none" src="${escapeHtml(src)}"></video>`)
    .join('')
  const links = d.links.map((u) => `<a href="${escapeHtml(u)}" target="_blank" rel="noopener">相关页面 ↗</a>`).join('')
  return images + videos + links
}

export function renderDetailBody(d) {
  const branches = d.variants ?? []
  if (!branches.length) return renderDetailText(d) + renderDetailMedia(d)
  const items = branches
    .map((variant) => `<details class="guide-variant"><summary>${escapeHtml(variant.label)}</summary><div class="guide-variant-body">${renderDetailText(variant)}${renderDetailMedia(variant)}</div></details>`)
    .join('')
  return `${renderDetailText(d)}<p class="guide-hint">先对照游戏里的任务名称，再展开对应的指南。</p><div class="guide-variants">${items}</div>`
}

function hasOverviewContent(detail) {
  return Boolean(detail && (detail.text?.trim() || detail.images.length))
}

function thumbUrl(src) {
  const match = /^(.*)history\/media\/([a-f0-9]{24})\.webp$/.exec(src)
  return match ? `${match[1]}history/thumbs/${match[2]}.webp` : src
}

function overviewDialogId(id, detail) {
  if (!hasOverviewContent(detail)) return null
  return detail.images.length > 0 ? `image-${id}` : `detail-${id}`
}

function overviewPreview(detail, id, title, emptyText) {
  if (!detail) return `<figure class="photo-note photo-note-empty"><figcaption class="note-empty-text">${escapeHtml(emptyText)}</figcaption></figure>`
  if (!hasOverviewContent(detail)) return `<figure class="photo-note photo-note-empty"><figcaption class="note-empty-text">图片暂缺</figcaption></figure>`
  const text = detail.text ? `<p class="preview-text">${escapeHtml(detail.text)}</p>` : ''
  const firstImage = detail.images[0]
  if (firstImage) {
    return `<a class="overview-trigger" href="#image-${id}" data-dialog="image-${id}" aria-haspopup="dialog" aria-label="查看${title}大图">${text}<figure class="photo-note"><div class="photo-window"><img src="${escapeHtml(thumbUrl(firstImage))}" data-original="${escapeHtml(firstImage)}" alt="${title}首图" loading="lazy" decoding="async"></div><figcaption>查看大图 ↗</figcaption></figure></a>`
  }
  return `<a class="overview-trigger" href="#${id}-details" data-dialog="detail-${id}" aria-haspopup="dialog" aria-label="查看${title}详情">${text}<span class="preview-caption">查看详情 ↗</span></a>`
}

function overviewDialogs(data) {
  return [['weather', '天气预报', data.weather], ['calendar', '本月日历', data.calendar]]
    .filter(([, , detail]) => hasOverviewContent(detail))
    .map(([id, title, detail]) => {
      const close = '<button type="button" class="dialog-close" aria-label="关闭详情"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></button>'
      if (detail.images.length > 0) {
        const images = detail.images.map((src) => linkedImage(src, title)).join('')
        return `<dialog class="detail-dialog image-dialog" id="image-${id}" data-has-text="false" aria-labelledby="dialog-title-image-${id}"><div class="dialog-bar"><span>${title}</span>${close}</div><div class="dialog-scroll"><h2 id="dialog-title-image-${id}">${title}</h2>${images}</div></dialog>`
      }
      return `<dialog class="detail-dialog" id="detail-${id}" data-has-text="true" aria-labelledby="dialog-title-${id}"><div class="dialog-bar"><span>${title}</span>${close}</div><div class="dialog-scroll"><h2 id="dialog-title-${id}">${title}</h2>${detail.text ? `<p>${escapeHtml(detail.text)}</p>` : ''}</div></dialog>`
    })
    .join('')
}

function hasTaskDetail(detail) {
  return Boolean(detail && (detail.text?.trim() || detail.sections.some(s => s.text?.trim() && !/任务|标题/.test(s.label)) || detail.images.length || detail.videos.length || detail.links.length || (detail.variants?.length ?? 0) > 0))
}

function taskNotes(data, detailByKeyword) {
  if (data.tasks.length === 0) return '<p class="empty">今日暂无任务数据</p>'
  return data.tasks.map((t) => {
    const d = t.keyword ? detailByKeyword.get(t.keyword) : null
    const sections = d?.sections.filter((s) => s.text.trim() && !/任务|标题/.test(s.label)) ?? []
    const labelChars = Math.min(4, Math.max(0, ...sections.slice(0, 2).map(s => s.label.length)))
    const labelStyle = labelChars > 2 ? ` style="--note-label-width:calc(var(--font-xs) * ${labelChars})"` : ''
    const brief = sections.slice(0, 2).map((s) => `<p><span class="note-label">${escapeHtml(s.label)}</span>${escapeHtml(s.text.replace(/^\s*[0-9]+[.、]\s*/gm, '').replaceAll('\n', '；').slice(0, 92))}${s.text.length > 92 ? '…' : ''}</p>`).join('') || (d?.text ? `<p>${escapeHtml(d.text.slice(0, 150))}</p>` : '')
    const action = hasTaskDetail(d) ? `<a class="detail-trigger" href="#task-${t.number}" data-dialog="detail-${t.number}" aria-haspopup="dialog">查看图文 <span aria-hidden="true">↗</span></a>` : ''
    return `<article class="task-note" id="task-${t.number}"><div class="note-top"><span class="note-index">任务 ${String(t.number).padStart(2, '0')}</span><label class="check-label"><input type="checkbox" data-complete="${t.number}" aria-label="标记任务 ${t.number} 已完成"><span></span></label></div><h3>${escapeHtml(t.text)}</h3><div class="note-brief"${labelStyle}>${brief}</div>${action}</article>`
  }).join('')
}

function taskDialogs(data, detailByKeyword, date) {
  return data.tasks.map((t) => {
    const d = t.keyword ? detailByKeyword.get(t.keyword) : null
    if (!hasTaskDetail(d)) return ''
    return `<dialog class="detail-dialog" id="detail-${t.number}" aria-labelledby="dialog-title-${t.number}"><div class="dialog-bar"><span class="dialog-context"><span>任务 ${String(t.number).padStart(2, '0')}</span><time datetime="${escapeHtml(date)}">${escapeHtml(date)}</time></span><button type="button" class="dialog-close" aria-label="关闭详情"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></button></div><div class="dialog-scroll"><h2 id="dialog-title-${t.number}">${escapeHtml(t.text)}</h2>${renderDetailBody(d)}</div></dialog>`
  }).join('')
}

function candleSection(data, detailByKeyword) {
  const seasonGuide = detailByKeyword.get(seasonGuidePolicy.keyword)
  const season = []
  if (data.seasonCandleMap) {
    season.push(`<p class="season-line">今日季节蜡烛所在地图：<strong>${escapeHtml(data.seasonCandleMap)}</strong></p>`)
  }
  const links = (seasonGuide?.links ?? []).map(normalizeSeasonGuideLink).filter(Boolean)
  const guides = links.length ? [...new Set(links)] : [SEASON_GUIDE_URL]
  season.push(guides.map(url => `<div class="guide-paper"><iframe class="season-guide" src="${escapeHtml(url)}" title="季节蜡烛探索指南" loading="lazy" referrerpolicy="no-referrer" sandbox="allow-scripts allow-same-origin"></iframe></div>`).join(''))
  return season.join('')
}

function overviewLine(data) {
  const bits = []
  if (data.tasks.length > 0) bits.push(`今日 ${data.tasks.length} 个任务`)
  if (data.seasonCandleMap) bits.push(`季节蜡烛在${data.seasonCandleMap}`)
  if (data.weather) bits.push(`天气：${data.weather.text}`)
  return bits.join(' · ')
}

function datePicker(current, dates, archive, weekday) {
  const model = dateNavigation(dates, current)
  const parts = current.split('-')
  const href = date => (archive ? '' : 'archive/') + date + '.html'
  const turn = (date, label) => date ? `<a href="${href(date)}" aria-label="${label}：${escapeHtml(date)}">${label}</a>` : `<span aria-disabled="true">${label}</span>`
  const months = model.months.map((month, index) => `<div class="calendar-month" data-month="${index}"${index !== model.monthIndex ? ' hidden' : ''}><div class="calendar-month-head"><button type="button" data-month-change="-1" aria-label="上个月"${index === 0 ? ' disabled' : ''}>‹</button><strong>${month.year} 年 ${month.number} 月</strong><button type="button" data-month-change="1" aria-label="下个月"${index === model.months.length - 1 ? ' disabled' : ''}>›</button></div><div class="calendar-week" aria-hidden="true">${['一','二','三','四','五','六','日'].map(day => `<span>${day}</span>`).join('')}</div><div class="calendar-grid">${'<span aria-hidden="true"></span>'.repeat(month.offset)}${month.days.map(day => day.available ? `<a href="${href(day.date)}" class="calendar-day${day.selected ? ' is-selected' : ''}" aria-label="${month.year}年${month.number}月${day.day}日"${day.selected ? ' aria-current="date"' : ''}>${day.day}</a>` : `<span class="calendar-day is-unavailable" aria-disabled="true">${day.day}</span>`).join('')}</div></div>`).join('')
  return `<details class="date-picker"><summary class="date-stamp" aria-label="选择日期，当前 ${escapeHtml(current)}" title="选择日期"><span>${parts[0]} / ${parts[1]}</span><strong>${parts[2]}</strong><span>${weekday} <svg class="date-caret" width="10" height="6" viewBox="0 0 10 6" aria-hidden="true"><path d="m1 1 4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5"/></svg></span></summary><div class="date-panel"><div class="date-panel-caption"><span>日期手记</span><a href="${archive ? '../index.html' : 'index.html'}">最新记录 ↗</a></div>${months}<nav class="date-turn" aria-label="按天翻页">${turn(model.previous, '上一天')}<span>${model.dates.length} 天记录</span>${turn(model.next, '下一天')}</nav></div></details>`
}

function themeToggle(assets = 'assets/') {
  return `<button type="button" class="theme-toggle" hidden disabled aria-label="切换到暗色模式" title="切换到暗色模式"><img class="theme-icon theme-icon-sun" src="${assets}theme-sun.webp" alt="" decoding="async" width="48" height="48"><img class="theme-icon theme-icon-moon" src="${assets}theme-moon.webp" alt="" decoding="async" width="48" height="48"></button>`
}

function beijingDateOf(iso) {
  const time = Date.parse(iso)
  if (!Number.isFinite(time)) return null
  return new Date(time + 8 * 3600_000).toISOString().slice(0, 10)
}

function beijingStamp(iso) {
  const date = beijingDateOf(iso)
  return date ? date.replaceAll('-', '.') : '日期未知'
}

export function noticeForDate(notices, date) {
  const effective = (notices ?? []).filter((item) => {
    const seen = beijingDateOf(item.first_seen_at)
    return seen !== null && seen <= date
  })
  if (effective.length === 0) return null
  const sameDay = effective.filter(item => beijingDateOf(item.first_seen_at) === date)
  return { latest: (sameDay.length > 0 ? sameDay : effective).at(-1), sameDay }
}

function noticeForHome(notices) {
  const versions = notices ?? []
  if (versions.length === 0) return null
  const latest = versions.at(-1)
  const day = beijingDateOf(latest.first_seen_at)
  const sameDay = versions.filter(item => beijingDateOf(item.first_seen_at) === day)
  return { latest, sameDay }
}

function noticeBrief(record) {
  return escapeHtml(String(record.message)
    .replace(/\s*\n+\s*/g, ' ')
    .replace(/([：、，。；！？】》）])\s+/g, '$1')
    .replace(/\s+([（【《])/g, '$1')
    .trim()
    .slice(0, 240))
}

function linkifyNotice(text) {
  return escapeHtml(String(text)).replace(/https?:\/\/[^\s<>"'）)]+/gi, (match) => {
    const canonical = normalizeNoticeLink(match)
    return canonical ? `<a class="notice-link" href="${canonical}" target="_blank" rel="noopener noreferrer">${match}</a>` : match
  })
}

function renderNoticeText(text) {
  const blocks = []
  let run = null
  const flush = () => {
    if (run) {
      blocks.push(run)
      run = null
    }
  }
  const push = (type, item) => {
    if (run?.type === type) run.items.push(item)
    else {
      flush()
      run = { type, items: [item] }
    }
  }
  for (const line of String(text).split('\n')) {
    const heading = /^\s*【(.+)】\s*$/.exec(line)
    const num = /^\s*\d+[.、]\s*(.+)$/.exec(line)
    const dot = /^\s*[·•⋅]\s*(.+)$/.exec(line)
    if (num) push('num', num[1])
    else if (dot) push('dot', dot[1])
    else {
      flush()
      if (heading) blocks.push({ type: 'section', text: heading[1] })
      else if (line.trim()) blocks.push({ type: 'p', text: line.trim() })
    }
  }
  flush()

  const groups = []
  for (const block of blocks) {
    const previous = groups.at(-1)
    if (block.type === 'dot' && previous?.type === 'num') previous.subs.set(previous.items.length - 1, block.items)
    else if (block.type === 'num' && previous?.type === 'num') previous.items.push(...block.items)
    else groups.push(block.type === 'num' ? { ...block, subs: new Map() } : { ...block })
  }

  const list = (tag, items, className = '') => `<${tag}${className}>${items.map((item) => `<li>${linkifyNotice(item)}</li>`).join('')}</${tag}>`
  return groups.map((group) => {
    if (group.type === 'section') return `<h4 class="notice-section">${escapeHtml(group.text)}</h4>`
    if (group.type === 'p') return `<p>${linkifyNotice(group.text)}</p>`
    if (group.type === 'dot') return list('ul', group.items, ' class="notice-sub"')
    const items = group.items.map((item, index) => {
      const sub = group.subs.get(index)
      return `<li>${linkifyNotice(item)}${sub ? list('ul', sub, ' class="notice-sub"') : ''}</li>`
    }).join('')
    return `<ol>${items}</ol>`
  }).join('')
}

function noticeSection(found) {
  if (!found) return ''
  const { latest, sameDay } = found
  const count = sameDay.length > 1 ? `<span class="notice-count">当日更新 ${sameDay.length} 次</span>` : ''
  return `<section id="notice" aria-labelledby="h-notice"><div class="section-head"><div><h2 id="h-notice">游戏公告</h2></div>${count}</div><article class="notice-paper" data-section-dialog="detail-notice"><div class="notice-top"><h3 class="notice-title">${escapeHtml(latest.title)}</h3><time class="notice-time" datetime="${escapeHtml(latest.published_at)}">${escapeHtml(beijingStamp(latest.published_at))}</time></div><p class="notice-brief">${noticeBrief(latest)}</p><a class="detail-trigger" href="#notice-details" data-dialog="detail-notice" aria-haspopup="dialog">查看全文 <span aria-hidden="true">↗</span></a></article></section>`
}

function noticeDialog(found) {
  if (!found) return ''
  const { latest, sameDay } = found
  const versions = sameDay.length > 1 ? sameDay : [latest]
  const body = versions.map((item) => `${versions.length > 1 ? `<h4 class="notice-version">${escapeHtml(beijingStamp(item.first_seen_at))}</h4>` : ''}${renderNoticeText(item.message)}`).join('')
  return `<dialog class="detail-dialog notice-dialog" id="detail-notice" data-has-text="true" aria-labelledby="dialog-title-notice"><div class="dialog-bar"><span class="dialog-context"><span>游戏公告</span><time datetime="${escapeHtml(latest.published_at)}">${escapeHtml(beijingStamp(latest.published_at))}</time></span><button type="button" class="dialog-close" aria-label="关闭详情"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></button></div><div class="dialog-scroll"><h2 id="dialog-title-notice">${escapeHtml(latest.title)}</h2>${body}</div></dialog>`
}

function renderFooter({ archive = false, date, api = false, app = false } = {}) {
  const nested = archive || app
  const dataHref = nested ? '../data.json' : 'data.json'
  const home = nested ? '<a href="../index.html">返回首页</a> · ' : api ? '<a href="index.html">返回首页</a> · ' : ''
  const guide = api ? '' : `<a href="${nested ? '../' : ''}api.html">API 使用说明</a>`
  const folio = date ? `<span>光遇每日任务</span><span>${date.replaceAll('-', '.')}</span><span>${date.split('-')[2]}</span>` : app ? '<span>光遇每日任务</span><span>下载 App</span><span>v1</span>' : '<span>光遇每日任务</span><span>API 使用说明</span><span>v1</span>'
  const share = archive || app ? '' : `<div class="footer-share"><div class="share-qr" aria-hidden="true">${SHARE_QR}</div><div class="share-copy"><p>扫码打开今日任务</p><div class="share-actions"><button type="button" class="share-button" data-share-url="${SITE_URL}/">分享本页</button><button type="button" class="share-button share-image" data-share-image="${SITE_URL}/preview/share.jpg${date ? `?v=${date}` : ''}">分享图片</button></div></div></div>`
  // 下载页不引用 GitHub，保持页面内只有同源下载地址。
  const feedback = app ? '' : '<p>版权问题反馈：<a href="https://github.com/GuNanOvO/skydaily/issues" target="_blank" rel="noopener noreferrer">仓库 Issues ↗</a></p>'
  return `<footer class="journal-footer"><div class="footer-info"><div class="footer-copyright"><p>图文来源于游戏内「小精灵」，版权归原权利人所有。</p>${feedback}</div>${share}<nav aria-label="页脚导航"><div>${home}<a href="${dataHref}">完整数据</a></div>${guide}<a class="back-top" href="#page-top">回到页首 ↑</a></nav></div><div class="footer-folio">${folio}</div></footer>`
}

export function renderApiGuide(date) {
  const endpoints = [
    ['/v1/daily', '今日完整数据'],
    ['/v1/daily/tasks', '今日任务列表'],
    ['/v1/daily/tasks/1', '指定任务与攻略，编号 1–4'],
    ['/v1/daily/weather', '今日天气'],
    ['/v1/daily/calendar', '本月日历'],
    ['/v1/daily/candles', '蜡烛相关数据'],
    ['/v1/notice', '当前游戏公告'],
    ['/v1/notice/history', '公告变更历史'],
    ['/v1/daily/YYYY-MM-DD', '指定日期的完整数据，最近 30 天'],
  ]
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><meta name="theme-color" content="#f4f4f4"><meta name="keywords" content="光遇国服, 光遇国服 API, 国服每日任务 API, 光遇国服数据接口, JSON 接口, API 使用说明"><title>API 使用说明 · 光遇国服每日任务</title>${faviconLinks()}<script src="assets/theme.js?v=${ASSET_VERSION}"></script><link rel="stylesheet" href="assets/notebook.css?v=${ASSET_VERSION}"></head><body>
<main id="page-top" class="api-guide">${themeToggle()}<div class="binding" aria-hidden="true"></div>
<header class="page-header"><h1>API 使用说明</h1><p class="api-meta">GET · JSON · 北京时间</p></header>
<nav class="jump" aria-label="页面导航"><a href="index.html">返回每日任务</a><a href="#endpoints">接口</a><a href="#example">示例</a><a href="#response">返回数据</a><a href="app/">下载 App</a></nav>
<section><h2>服务地址</h2><p><code>${SITE_URL}</code></p><p>接口提供光遇国服数据，无需密钥，支持跨域请求。</p><p>同一 IP 每分钟约 30 次；超限返回 429，请按 Retry-After 等待后重试。</p></section>
<section id="endpoints"><h2>接口</h2><dl class="api-endpoints">${endpoints.map(([path, text]) => `<div><dt><code>${path}</code></dt><dd>${text}</dd></div>`).join('')}</dl></section>
<section id="example"><h2>调用示例</h2><pre class="api-example"><code>curl ${SITE_URL}/v1/daily/tasks

curl ${SITE_URL}/v1/daily/${escapeHtml(date)}</code></pre></section>
<section id="response"><h2>返回数据</h2><p><code>date</code> 为记录日期，<code>data</code> 为接口内容，<code>meta.fetched_at</code> 为采集时间，<code>errors</code> 为采集异常列表。</p><p>分区接口使用相同返回结构，<code>data</code> 仅包含对应分区。</p><dl class="api-endpoints api-status"><div><dt><code>200</code></dt><dd>请求成功</dd></div><div><dt><code>400</code></dt><dd>日期或任务编号格式错误</dd></div><div><dt><code>404</code></dt><dd>记录或任务不存在</dd></div><div><dt><code>500</code></dt><dd>服务异常</dd></div></dl></section>
${renderFooter({ api: true })}</main><script src="assets/notebook.js?v=${ASSET_VERSION}" defer></script></body></html>`
}

export function renderPage(envelope, options = {}) {
  const { meta } = envelope
  const data = structuredClone(envelope.data)
  const archive = Boolean(options.archive)
  const notices = options.notices ?? []
  const assets = archive ? '../assets/' : 'assets/'
  for (const detail of [...data.taskDetails, ...data.candles, data.weather, data.calendar]) {
    if (!detail) continue
    detail.images = detail.images.map(src => {
      const match = /\/history\/media\/([a-f0-9]{24}\.webp)$/.exec(src)
      return match ? (archive ? '../' : '') + 'history/media/' + match[1] : src
    })
  }
  const picker = datePicker(envelope.date, options.dates ?? [envelope.date], archive, ['星期日','星期一','星期二','星期三','星期四','星期五','星期六'][new Date(envelope.date + 'T12:00:00+08:00').getUTCDay()])
  const dateParts = envelope.date.split('-')
  const detailByKeyword = new Map(data.taskDetails.map((d) => [d.keyword, d]))
  const status = [
    { text: `更新于 ${beijingTime(meta.fetched_at)}（北京时间）` },
    meta.stale ? { text: '数据非最新', warn: true } : null,
  ].filter(Boolean).map((s) => `<span${s.warn ? ' class="warn"' : ''}>${s.text}</span>`).join('')

  const summary = overviewLine(data)
  const shareImage = `${SITE_URL}/preview/share.jpg?v=${envelope.date}`

  const weather = overviewPreview(data.weather, 'weather', '天气预报', '今日无天气数据')
  const calendar = overviewPreview(data.calendar, 'calendar', '本月日历', '今日无日历数据')
  const sectionDialog = (id, detail) => {
    const dialogId = overviewDialogId(id, detail)
    return dialogId ? ` data-section-dialog="${dialogId}"` : ''
  }
  const notice = archive ? noticeForDate(notices, envelope.date) : noticeForHome(notices)


  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<meta name="theme-color" content="#f4f4f4">
<meta name="keywords" content="光遇国服, 光遇国服每日任务, ${archive ? '光遇国服历史任务, 国服任务归档' : '光遇国服今日任务'}, 光遇国服任务攻略, 国服季节蜡烛, 光遇国服天气预报, 光遇国服日历">
<meta property="og:type" content="website">
<meta property="og:title" content="光遇国服每日任务 · ${escapeHtml(envelope.date)}">
${summary ? `<meta property="og:description" content="${escapeHtml(summary)}">` : ''}
<meta property="og:image" content="${escapeHtml(shareImage)}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<title>光遇国服每日任务 · ${escapeHtml(envelope.date)}</title>
${faviconLinks(assets)}
<script src="${assets}theme.js?v=${ASSET_VERSION}"></script>
<link rel="stylesheet" href="${assets}notebook.css?v=${ASSET_VERSION}">
</head>
<body>
<main id="page-top" data-date="${escapeHtml(envelope.date)}">
${themeToggle(assets)}
<div class="binding" aria-hidden="true"></div><span class="journal-sticker sticker-butterfly-margin" aria-hidden="true"></span>
<header class="page-header"><div class="header-body"><div><h1>光遇每日任务</h1><span class="journal-sticker sticker-cape" aria-hidden="true"></span><div class="status">${status}</div></div>${picker}</div></header>
<nav class="jump" aria-label="页面导航"><span class="journal-sticker sticker-star" aria-hidden="true"></span><a href="#tasks">每日任务 <small>${data.tasks.length}</small></a><a href="#weather">天气预报</a><a href="#calendar">本月日历</a><a href="#candles">季节蜡烛</a>${notice ? '<a href="#notice">游戏公告</a>' : ''}<a href="${archive ? '../app/' : 'app/'}">下载 App</a></nav>
<section id="tasks" aria-labelledby="h-tasks"><div class="section-head"><div><h2 id="h-tasks">每日任务</h2><span class="journal-sticker sticker-jelly" aria-hidden="true"></span></div><span class="progress" role="status" aria-live="polite" aria-atomic="true"><span id="complete-count">0</span> / ${data.tasks.length} 已完成</span></div><div class="tasks">${taskNotes(data, detailByKeyword)}</div></section>
<div class="overview"><section id="weather"${sectionDialog('weather', data.weather)} aria-labelledby="h-weather"><h2 id="h-weather">天气预报 <span class="journal-sticker sticker-manta" aria-hidden="true"></span></h2><div class="info-card">${weather}</div></section><section id="calendar"${sectionDialog('calendar', data.calendar)} aria-labelledby="h-calendar"><h2 id="h-calendar">本月日历 <span class="journal-sticker sticker-butterfly" aria-hidden="true"></span></h2><div class="info-card">${calendar}</div></section></div>
<section id="candles" aria-labelledby="h-candles"><h2 id="h-candles">季节蜡烛</h2><span class="journal-sticker sticker-candles" aria-hidden="true"></span>${candleSection(data, detailByKeyword)}</section>
${noticeSection(notice)}
${renderFooter({ archive, date: envelope.date })}
</main>
${taskDialogs(data, detailByKeyword, envelope.date)}
${overviewDialogs(data)}
${noticeDialog(notice)}
<script src="${assets}notebook.js?v=${ASSET_VERSION}" defer></script>
</body>
</html>`
}

// 首页与归档页的「下载 App」入口共用这份截图清单，文件由发布方提供。
const APP_SHOT_META = [
  { title: '每日任务', caption: '任务勾选与完成进度' },
  { title: '饭点提醒', caption: '饭点状态与提醒时间' },
  { title: '悬浮任务面板', caption: '在其他应用中查看今日任务' },
]
const APP_SCREENSHOTS = APP_SCREENSHOT_FILES.map((file, index) => ({ file, ...APP_SHOT_META[index] }))

const ANDROID_NAMES = new Map([[28, 'Android 9'], [29, 'Android 10'], [30, 'Android 11'], [31, 'Android 12'], [32, 'Android 12L'], [33, 'Android 13'], [34, 'Android 14'], [35, 'Android 15'], [36, 'Android 16']])

function androidRequirement(minSdk) {
  const name = ANDROID_NAMES.get(minSdk)
  return name ? `${name}（API ${minSdk}）及以上` : `Android API ${minSdk} 及以上`
}

/**
 * 渲染 /app/ 下载页；release 为 null 时只说明尚未发布，不提供下载按钮。
 * @param {{ versionName: string, versionCode: number, publishedAt: string, minSdk: number, size: number, sha256: string, apkPath: string, notes: string[] } | null} release
 * @param {{ assets?: string }} [options]
 * @returns {string}
 */
export function renderAppPage(release, options = {}) {
  const assets = options.assets ?? '../assets/'
  const shots = APP_SCREENSHOTS.map(({ file, title, caption }) => `<figure class="app-shot"><div class="app-shot-frame"><img src="${assets}app/${file}" alt="${title}" loading="lazy" decoding="async"></div><figcaption>${title} · ${caption}</figcaption></figure>`).join('')

  const status = release ? `<span>当前版本 ${escapeHtml(release.versionName)}</span><span>更新于 ${escapeHtml(beijingStamp(release.publishedAt))}</span>` : '<span class="warn">尚未发布</span><span>暂无可下载的安装包</span>'
  const facts = release
    ? `<dl class="app-facts">
<div><dt>版本</dt><dd>${escapeHtml(release.versionName)}（${release.versionCode}）</dd></div>
<div><dt>更新于</dt><dd>${escapeHtml(beijingStamp(release.publishedAt))} ${escapeHtml(beijingTime(release.publishedAt))}（北京时间）</dd></div>
<div><dt>安装包</dt><dd>${release.size} 字节（约 ${(release.size / 1048576).toFixed(2)} MB）</dd></div>
<div><dt>校验值</dt><dd><code>${escapeHtml(release.sha256)}</code>（SHA-256）</dd></div>
<div><dt>系统要求</dt><dd>${escapeHtml(androidRequirement(release.minSdk))}</dd></div>
</dl>
<a class="app-download" href="${escapeHtml(release.apkPath)}" download>下载 APK</a>`
    : '<p class="app-empty">还没有发布可下载的版本。发布完成后，这里会显示当前安装包、更新说明和界面预览。</p>'
  const notes = release && release.notes.length > 0
    ? `<section id="notes" aria-labelledby="h-notes"><h2 id="h-notes">更新内容</h2><ul class="app-notes">${release.notes.map((note) => `<li>${escapeHtml(note)}</li>`).join('')}</ul></section>`
    : ''

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<meta name="theme-color" content="#f4f4f4">
<meta name="keywords" content="SkyDaily 下载, 光遇国服 App, 光遇每日任务 App, 安卓安装包">
<title>下载 App · SkyDaily</title>
${faviconLinks(assets)}
<script src="${assets}theme.js?v=${ASSET_VERSION}"></script>
<link rel="stylesheet" href="${assets}notebook.css?v=${ASSET_VERSION}">
<style>
.app-page h1{top:0;line-height:32px}
.app-page .jump{height:auto;row-gap:0}
.app-page section+section{margin-top:32px}
.app-page .app-facts{display:grid;gap:0 24px;margin:16px 0 0;padding:16px 24px;background:#fff9e9;outline:1px solid #cbc9cc50;box-shadow:var(--sticker-shadow)}
.app-page .app-facts>div{display:flex;gap:12px;line-height:32px;font-size:var(--font-sm);min-height:32px}
.app-page .app-facts dt{flex:0 0 5em;color:var(--muted)}
.app-page .app-facts dd{margin:0;min-width:0;overflow-wrap:anywhere}
.app-page .app-facts code{font:inherit}
.app-page .app-download{display:flex;width:max-content;align-items:center;justify-content:center;margin-top:16px;padding:0 32px;height:64px;font-size:var(--font-sm);text-decoration:none;background:#f4e7bd;border-radius:2px;box-shadow:var(--sticker-shadow)}
.app-page .app-download:hover{filter:brightness(.96)}
.app-page .app-hint{margin:0;color:var(--muted);font-size:var(--font-xs);line-height:32px;overflow-wrap:anywhere}
.app-page .app-qr+.app-hint{margin-top:32px}
.app-page .app-empty{margin:32px 0 0;padding:0 24px;background:#f4f2f6;border-left:2px solid #c9c3ce;color:var(--muted);font-size:var(--font-sm)}
.app-page .app-shots{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:32px 24px;margin:32px 0 0}
.app-page .app-shot{margin:0;padding:0 8px;background:#fffefc;outline:1px solid #d7d4db66;box-shadow:var(--sticker-shadow)}
.app-page .app-shot-frame{display:flex;align-items:center;justify-content:center;height:432px;margin:8px 0;background:#f4f2f6}
.app-page .app-shot img{width:100%;height:100%;object-fit:contain;background:none}
.app-page .app-shot figcaption{line-height:32px;font-size:var(--font-xs);color:var(--muted);text-align:right}
.app-page .app-notes,.app-page .app-steps{margin:32px 0 0;padding-left:24px}
.app-page .app-notes li,.app-page .app-steps li{font-size:var(--font-sm);line-height:32px;margin:0}
.app-page .app-qr{width:160px;margin:32px 0 0;padding:16px;background:#fff;outline:1px solid #d7d4db66;box-shadow:var(--sticker-shadow)}
.app-page .app-qr svg{display:block;width:100%;height:auto}
:root[data-theme="dark"] .app-page .app-facts,:root[data-theme="dark"] .app-page .app-shot{background:#2c2a36;outline-color:#615b6c70}
:root[data-theme="dark"] .app-page .app-shot-frame{background:#1b1d24}
:root[data-theme="dark"] .app-page .app-download{background:#e6d9a8;color:#353539}
:root[data-theme="dark"] .app-page .app-empty{background:#2b2d36;border-left-color:#6b6176;color:var(--muted)}
@media(max-width:800px){.app-page .app-shots{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:540px){.app-page .app-shots{grid-template-columns:1fr;gap:32px}.app-page .app-facts>div{display:block}.app-page .app-facts dt{line-height:32px}.app-page .app-download{width:100%;padding:0 16px}.app-page .jump{gap:0 4px}}
</style>
</head>
<body>
<main id="page-top" class="app-page">
${themeToggle(assets)}
<div class="binding" aria-hidden="true"></div>
<header class="page-header"><div class="header-body"><div><h1>下载 App</h1><div class="status">${status}</div></div></div></header>
<nav class="jump" aria-label="页面导航"><a href="../index.html">返回每日任务</a><a href="#download">下载</a><a href="#preview">界面预览</a>${notes ? '<a href="#notes">更新内容</a>' : ''}${release ? '<a href="#install">安装说明</a>' : ''}</nav>
<section id="download" aria-labelledby="h-download"><h2 id="h-download">下载</h2><p class="app-hint">SkyDaily 安卓客户端：每日任务、饭点提醒与悬浮面板。</p>${facts}</section>
<section id="preview" aria-labelledby="h-preview"><h2 id="h-preview">界面预览</h2><div class="app-shots">${shots}</div></section>
${notes}
${release ? '<section id="install" aria-labelledby="h-install"><h2 id="h-install">安装说明</h2><ol class="app-steps"><li>点击「下载 APK」，保存安装包。</li><li>首次安装时，按系统提示允许安装未知应用。</li><li>打开安装包完成安装；覆盖安装会保留已有数据。</li><li>安装后在应用内可以手动检查更新。</li></ol></section>' : ''}
<section id="qr" aria-labelledby="h-qr"><h2 id="h-qr">扫码打开</h2><div class="app-qr" aria-hidden="true">${APP_QR}</div><p class="app-hint">扫码或在浏览器中打开 ${escapeHtml(SITE_URL)}${APP_PAGE_PATH}。</p></section>
${renderFooter({ app: true })}
</main>
<script src="${assets}notebook.js?v=${ASSET_VERSION}" defer></script>
</body>
</html>`
}

/**
 * 把暂存目录中的发布文件复制到站点输出目录，并写入下载页。
 * @param {{ root?: string, bundle?: string | null }} [options]
 * @returns {Promise<{ release: object | null, releases: number }>}
 */
export async function integrateAppRelease({ root = 'out', bundle = process.env.APP_RELEASE_JSON } = {}) {
  const summary = bundle ? JSON.parse(await readFile(bundle, 'utf8')) : null
  const base = bundle ? dirname(resolve(bundle)) : process.cwd()
  const latestPath = summary?.latest ? resolve(base, summary.latest) : null
  const release = latestPath ? releaseObject(JSON.parse((await readFile(latestPath)).toString('utf8'))) : null
  if (summary?.latest && !release) throw new Error('app release manifest is invalid')
  const releases = summary?.releases ?? []
  // 先校验全部候选，再写入，避免清单指向缺失或损坏的安装包。
  const verified = []
  for (const item of releases) {
    const manifestPath = resolve(base, item.manifest)
    const apkPath = resolve(base, item.apk)
    const manifest = releaseObject(JSON.parse((await readFile(manifestPath)).toString('utf8')))
    if (!manifest || manifest.versionCode !== item.versionCode) throw new Error('staged app release is invalid')
    const bytes = await readFile(apkPath)
    if (bytes.length !== manifest.size) throw new Error('staged app release size does not match')
    if (createHash('sha256').update(bytes).digest('hex') !== manifest.sha256) throw new Error('staged app release hash does not match')
    assertApkMatchesRelease(apkPath, manifest)
    verified.push({ manifest, apkPath, manifestPath })
  }
  if (release) {
    if (!verified.some((item) => item.manifest.versionCode === release.versionCode)) throw new Error('latest app release is not staged')
    if (verified.some((item) => item.manifest.versionCode > release.versionCode)) throw new Error('latest app release is not the newest staged version')
  }
  const target = join(root, 'app')
  await rm(target, { recursive: true, force: true })
  await mkdir(join(target, 'releases'), { recursive: true })
  for (const item of verified) {
    const dir = join(target, 'releases', String(item.manifest.versionCode))
    await mkdir(dir, { recursive: true })
    await cp(item.apkPath, join(dir, basename(item.apkPath)))
    await cp(item.manifestPath, join(dir, 'release.json'))
  }
  if (latestPath) await cp(latestPath, join(target, 'latest.json'))
  await writeFile(join(target, 'index.html'), renderAppPage(release, { assets: '../assets/' }))
  return { release, releases: verified.length }
}

export async function loadEnvelope() {
  const fileFlag = process.argv.indexOf('--file')
  if (fileFlag !== -1) {
    const { readFile } = await import('node:fs/promises')
    return JSON.parse(await readFile(process.argv[fileFlag + 1], 'utf8'))
  }
  const base = process.env.WORKER_URL
  if (!base) {
    console.error('需要 WORKER_URL 或 --file <path>')
    process.exit(2)
  }
  const resp = await fetch(`${base.replace(/\/$/, '')}/v1/daily`)
  if (!resp.ok) throw new Error(`fetch envelope: HTTP ${resp.status}`)
  const body = await resp.json()
  if (!body?.data) throw new Error('envelope malformed')
  return body
}

export async function loadNotices(file) {
  try {
    const { readFile } = await import('node:fs/promises')
    const archive = JSON.parse(await readFile(file, 'utf8'))
    return Array.isArray(archive?.versions) ? archive.versions : []
  } catch {
    return []
  }
}

if (process.argv[1]?.endsWith('render-pages.mjs')) {
  const envelope = await loadEnvelope()
  const noticesFile = process.env.NOTICES_FILE ?? 'history/notices.json'
  const notices = await loadNotices(noticesFile)
  if (process.env.PUBLISH_HISTORY === '1') await rm('out', { recursive: true, force: true })
  await mkdir('out/archive', { recursive: true })
  await rm('out/assets', { recursive: true, force: true })
  await cp(new URL('../assets', import.meta.url), 'out/assets', { recursive: true, filter: source => !source.endsWith('.md') })
  let dates = [envelope.date]
  if (process.env.PUBLISH_HISTORY === '1') {
    const { readFile } = await import('node:fs/promises')
    const { readHistory } = await import('./read-history.mjs')
    const index = JSON.parse(await readFile('history/index.json', 'utf8'))
    dates = index.dates.map(entry => entry.date)
    await rm('out/history', { recursive: true, force: true })
    await rm('out/archive', { recursive: true, force: true })
    await mkdir('out/archive', { recursive: true })
    await cp('history/daily', 'out/history/daily', { recursive: true })
    await cp('history/media', 'out/history/media', { recursive: true })
    try { await cp('history/thumbs', 'out/history/thumbs', { recursive: true }) } catch {}
    await cp('history/index.json', 'out/history/index.json')
    for (const entry of index.dates) {
      const record = readHistory(entry.date)
      await writeFile(`out/archive/${entry.date}.html`, renderPage(record, { archive: true, dates, notices }))
      await writeFile(`out/archive/${entry.date}.json`, JSON.stringify(record, null, 2))
    }
  }
  await writeFile('out/index.html', renderPage(envelope, { dates, notices }))
  await writeFile('out/api.html', renderApiGuide(envelope.date))
  await writeFile('out/CNAME', 'skydaily.nankki.com\n')
  await writeFile('out/data.json', JSON.stringify(envelope, null, 2))
  await writeFile(`out/archive/${envelope.date}.json`, JSON.stringify(envelope, null, 2))
  await writeFile(`out/archive/${envelope.date}.html`, renderPage(envelope, { archive: true, dates, notices }))
  try { await cp(noticesFile, 'out/history/notices.json') } catch {}
  const app = await integrateAppRelease({ root: 'out' })
  console.log(`rendered ${envelope.date}: index.html, data.json, archive/${envelope.date}.json, archive/${envelope.date}.html, app/index.html`)
  console.log(app.release ? `app release ${app.release.versionName} (${app.release.versionCode}), ${app.releases} 个安装包` : 'app release: 暂无发布')
}
