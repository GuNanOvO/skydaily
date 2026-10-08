import { dateNavigation } from './date-navigation.mjs'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import QRCode from 'qrcode'
import { normalizeSeasonGuideLink, seasonGuidePolicy } from '../src/link-policy.mjs'


const SEASON_GUIDE_URL = seasonGuidePolicy.fallback
const SITE_URL = (process.env.SITE_URL ?? 'https://skydaily.nankki.com').replace(/\/$/, '')
const ASSET_VERSION = createHash('sha256').update(readFileSync(new URL('../assets/notebook.css', import.meta.url))).update(readFileSync(new URL('../assets/notebook.js', import.meta.url))).digest('hex').slice(0, 12)
const ICON_VERSION = createHash('sha256').update(readFileSync(new URL('../assets/favicon.svg', import.meta.url))).digest('hex').slice(0, 12)
const SHARE_QR = await QRCode.toString(SITE_URL + '/', { type: 'svg', margin: 0 })

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
  return renderDetailText(d) + renderDetailMedia(d)
}

function hasOverviewContent(detail) {
  return Boolean(detail && (detail.text?.trim() || detail.images.length))
}

function overviewPreview(detail, id, title, emptyText) {
  if (!detail) return `<p class="empty">${emptyText}</p>`
  if (!hasOverviewContent(detail)) return `<figure class="photo-note"><div class="photo-window"><span class="image-unavailable">图片暂缺</span></div></figure>`
  const text = detail.text ? `<p class="preview-text">${escapeHtml(detail.text)}</p>` : '<div class="preview-text" aria-hidden="true"></div>'
  const firstImage = detail.images[0]
  const image = firstImage ? `<figure class="photo-note"><div class="photo-window"><img src="${escapeHtml(firstImage)}" alt="${title}首图" loading="lazy" decoding="async"></div><figcaption>查看详情 ↗</figcaption></figure>` : '<span class="preview-caption">查看详情 ↗</span>'
  return `<a class="overview-trigger" href="#${id}-details" data-dialog="detail-${id}" aria-haspopup="dialog" aria-label="查看${title}详情">${text}${image}</a>`
}

function overviewDialogs(data) {
  return [['weather', '天气预报', data.weather], ['calendar', '本月日历', data.calendar]].filter(([, , detail]) => hasOverviewContent(detail)).map(([id, title, detail]) => `<dialog class="detail-dialog" id="detail-${id}" data-has-text="${Boolean(detail.text?.trim())}" aria-labelledby="dialog-title-${id}"><div class="dialog-bar"><span>${title}</span><button type="button" class="dialog-close" aria-label="关闭详情"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></button></div><div class="dialog-scroll"><h2 id="dialog-title-${id}">${title}</h2>${detail.text ? `<p>${escapeHtml(detail.text)}</p>` : ''}${detail.images.map(src => linkedImage(src, title)).join('')}</div></dialog>`).join('')
}

function hasTaskDetail(detail) {
  return Boolean(detail && (detail.text?.trim() || detail.sections.some(s => s.text?.trim() && !/任务|标题/.test(s.label)) || detail.images.length || detail.videos.length || detail.links.length))
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

function renderFooter({ archive = false, date, api = false } = {}) {
  const dataHref = archive ? '../data.json' : 'data.json'
  const home = archive ? '<a href="../index.html">返回首页</a> · ' : api ? '<a href="index.html">返回首页</a> · ' : ''
  const guide = api ? '' : `<a href="${archive ? '../' : ''}api.html">API 使用说明</a>`
  const folio = date ? `<span>光遇每日任务</span><span>${date.replaceAll('-', '.')}</span><span>${date.split('-')[2]}</span>` : '<span>光遇每日任务</span><span>API 使用说明</span><span>v1</span>'
  const share = archive ? '' : `<div class="footer-share"><div class="share-qr" aria-hidden="true">${SHARE_QR}</div><div class="share-copy"><p>扫码打开今日任务</p><button type="button" class="share-button" data-share-url="${SITE_URL}/">分享本页</button></div></div>`
  return `<footer class="journal-footer"><div class="footer-info"><div class="footer-copyright"><p>图文来源于游戏内小精灵，版权归原权利人所有；本站内容仅供学习与交流。</p><p>获取最新资讯与完整攻略，请优先使用游戏内「小精灵」。</p><p>版权问题反馈：<a href="https://github.com/GuNanOvO/skydaily/issues" target="_blank" rel="noopener noreferrer">仓库 Issues ↗</a></p></div>${share}<nav aria-label="页脚导航"><div>${home}<a href="${dataHref}">完整数据</a></div>${guide}<a class="back-top" href="#page-top">回到页首 ↑</a></nav></div><div class="footer-folio">${folio}</div></footer>`
}

export function renderApiGuide(date) {
  const endpoints = [
    ['/v1/daily', '今日完整数据'],
    ['/v1/daily/tasks', '今日任务列表'],
    ['/v1/daily/tasks/1', '指定任务与攻略，编号 1–4'],
    ['/v1/daily/weather', '今日天气'],
    ['/v1/daily/calendar', '本月日历'],
    ['/v1/daily/candles', '蜡烛相关数据'],
    ['/v1/daily/YYYY-MM-DD', '指定日期的完整数据，最近 30 天'],
  ]
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><title>API 使用说明 · 光遇每日任务</title>${faviconLinks()}<link rel="stylesheet" href="assets/notebook.css?v=${ASSET_VERSION}"></head><body>
<main id="page-top" class="api-guide"><div class="binding" aria-hidden="true"></div>
<header class="page-header"><h1>API 使用说明</h1><p class="api-meta">GET · JSON · 北京时间</p></header>
<nav class="jump" aria-label="页面导航"><a href="index.html">返回每日任务</a><a href="#endpoints">接口</a><a href="#example">示例</a><a href="#response">返回数据</a></nav>
<section><h2>服务地址</h2><p><code>${SITE_URL}</code></p><p>公开接口无需密钥，支持跨域请求。</p><p>同一 IP 每分钟约 30 次；超限返回 429，请按 Retry-After 等待后重试。查询不触发数据采集。</p></section>
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


  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<meta name="theme-color" content="#f4f4f4">
<meta property="og:type" content="website">
<meta property="og:title" content="光遇每日任务 · ${escapeHtml(envelope.date)}">
${summary ? `<meta property="og:description" content="${escapeHtml(summary)}">` : ''}
<meta property="og:image" content="${escapeHtml(shareImage)}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<title>光遇每日任务 · ${escapeHtml(envelope.date)}</title>
${faviconLinks(assets)}
<link rel="stylesheet" href="${assets}notebook.css?v=${ASSET_VERSION}">
</head>
<body>
<main id="page-top" data-date="${escapeHtml(envelope.date)}">
<div class="binding" aria-hidden="true"></div><span class="journal-sticker sticker-butterfly-margin" aria-hidden="true"></span>
<header class="page-header"><div class="header-body"><div><h1>光遇每日任务</h1><span class="journal-sticker sticker-cape" aria-hidden="true"></span><div class="status">${status}</div></div>${picker}</div></header>
<nav class="jump" aria-label="页面导航"><span class="journal-sticker sticker-star" aria-hidden="true"></span><a href="#tasks">每日任务 <small>${data.tasks.length}</small></a><a href="#weather">天气预报</a><a href="#calendar">本月日历</a><a href="#candles">季节蜡烛</a></nav>
<section id="tasks" aria-labelledby="h-tasks"><div class="section-head"><div><h2 id="h-tasks">每日任务</h2><span class="journal-sticker sticker-jelly" aria-hidden="true"></span></div><span class="progress" role="status" aria-live="polite" aria-atomic="true"><span id="complete-count">0</span> / ${data.tasks.length} 已完成</span></div><div class="tasks">${taskNotes(data, detailByKeyword)}</div></section>
<div class="overview"><section id="weather"${hasOverviewContent(data.weather) ? ' data-section-dialog="detail-weather"' : ''} aria-labelledby="h-weather"><h2 id="h-weather">天气预报 <span class="journal-sticker sticker-manta" aria-hidden="true"></span></h2><div class="info-card">${weather}</div></section><section id="calendar"${hasOverviewContent(data.calendar) ? ' data-section-dialog="detail-calendar"' : ''} aria-labelledby="h-calendar"><h2 id="h-calendar">本月日历 <span class="journal-sticker sticker-butterfly" aria-hidden="true"></span></h2><div class="info-card">${calendar}</div></section></div>
<section id="candles" aria-labelledby="h-candles"><h2 id="h-candles">季节蜡烛</h2><span class="journal-sticker sticker-candles" aria-hidden="true"></span>${candleSection(data, detailByKeyword)}</section>
${renderFooter({ archive, date: envelope.date })}
</main>
${taskDialogs(data, detailByKeyword, envelope.date)}
${overviewDialogs(data)}
<script src="${assets}notebook.js?v=${ASSET_VERSION}" defer></script>
</body>
</html>`
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

if (process.argv[1]?.endsWith('render-pages.mjs')) {
  const { mkdir, writeFile, cp, rm } = await import('node:fs/promises')
  const envelope = await loadEnvelope()
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
    await cp('history/index.json', 'out/history/index.json')
    for (const entry of index.dates) {
      const record = readHistory(entry.date)
      await writeFile(`out/archive/${entry.date}.html`, renderPage(record, { archive: true, dates }))
      await writeFile(`out/archive/${entry.date}.json`, JSON.stringify(record, null, 2))
    }
  }
  await writeFile('out/index.html', renderPage(envelope, { dates }))
  await writeFile('out/api.html', renderApiGuide(envelope.date))
  await writeFile('out/CNAME', 'skydaily.nankki.com\n')
  await writeFile('out/data.json', JSON.stringify(envelope, null, 2))
  await writeFile(`out/archive/${envelope.date}.json`, JSON.stringify(envelope, null, 2))
  await writeFile(`out/archive/${envelope.date}.html`, renderPage(envelope, { archive: true, dates }))
  console.log(`rendered ${envelope.date}: index.html, data.json, archive/${envelope.date}.json, archive/${envelope.date}.html`)
}
