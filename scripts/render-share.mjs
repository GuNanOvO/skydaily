import { mkdir, rm, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'
import QRCode from 'qrcode'
import { escapeHtml, loadEnvelope } from './render-pages.mjs'

const SITE_URL = (process.env.SITE_URL ?? 'https://skydaily.nankki.com').replace(/\/$/, '')
const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

function cardHtml(envelope, qrSvg) {
  const { data } = envelope
  const [year, month, day] = envelope.date.split('-')
  const weekday = WEEKDAYS[new Date(envelope.date + 'T12:00:00+08:00').getUTCDay()]
  const tasks = data.tasks.map((task) => `<li><span>${task.number}</span>${escapeHtml(task.text)}</li>`).join('')
  const chips = []
  if (data.seasonCandleMap) chips.push(`<span class="chip candle">季节蜡烛：${escapeHtml(data.seasonCandleMap)}</span>`)
  if (data.weather?.text) chips.push(`<span class="chip weather">${escapeHtml(data.weather.text)}</span>`)
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><style>
*{box-sizing:border-box}
html,body{margin:0;width:1200px;height:630px;overflow:hidden}
body{font:24px/64px system-ui,-apple-system,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;color:#28342c;background:#fcfcfa url("assets/notebook-paper-texture.webp") repeat;background-size:512px}
.card{position:relative;height:630px;padding:32px 64px;display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:32px}
.card::before{content:"";position:absolute;inset:32px;pointer-events:none;background:repeating-linear-gradient(to bottom,transparent 0 62px,#d3dfe3 62px 64px)}
.main-col{display:flex;flex-direction:column}
.eyebrow{margin:0;line-height:64px;font-size:24px;letter-spacing:.2em;color:#7b8a80}
h1{margin:0;line-height:64px;font-size:52px;letter-spacing:.01em}
h1 small{margin-left:16px;font-size:28px;font-weight:400;color:#6b7a70}
ol{margin:0;padding:0;list-style:none}
li{display:flex;align-items:baseline;gap:16px;font-size:40px;line-height:64px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
li span{flex:0 0 36px;color:#8a6b4f;font-size:28px}
.chips{display:flex;gap:14px;margin:0;min-width:0}
.chip{font-size:28px;line-height:64px;padding:0 18px;background:#e7ece1;color:#3d4b42;outline:1px solid #cfd8c9;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.chip.weather{background:#e6ecf3;outline-color:#ccd7e4}
.foot{margin:0;font-size:24px;line-height:64px;color:#7b8a80}
.side{position:relative}
.qr{position:absolute;top:8px;right:4px;width:248px;height:248px;padding:14px;background:#fff;outline:1px solid #c5cec3;box-shadow:0 10px 22px rgba(38,59,50,.15);transform:rotate(1.5deg)}
.qr svg{display:block;width:100%;height:100%}
.sticker{position:absolute;height:auto}
.sticker-jelly{right:-4px;bottom:12px;width:150px;transform:rotate(9deg)}
.sticker-star{right:250px;bottom:72px;width:60px;transform:rotate(-12deg)}
</style></head><body>
<main class="card">
  <div class="main-col">
    <p class="eyebrow">SKY · DAILY GUIDE</p>
    <h1>${year}.${month}.${day}<small>${weekday}</small></h1>
    <ol>${tasks}</ol>
    <p class="chips">${chips.join('')}</p>
    <p class="foot">skydaily.nankki.com · 扫码看图文攻略</p>
  </div>
  <div class="side">
    <div class="qr">${qrSvg}</div>
    <img class="sticker sticker-star" src="assets/sky-star-handdrawn.svg" alt="">
    <img class="sticker sticker-jelly" src="assets/sky-jelly-handdrawn.webp" alt="">
  </div>
</main>
</body></html>`
}

const envelope = await loadEnvelope()
const qrSvg = await QRCode.toString(SITE_URL + '/', { type: 'svg', margin: 0 })
const root = resolve('out')
await mkdir(resolve(root, 'preview'), { recursive: true })
const htmlPath = resolve(root, 'share-card.html')
await writeFile(htmlPath, cardHtml(envelope, qrSvg))

const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 2, reducedMotion: 'reduce' })
  await page.goto('file://' + htmlPath, { waitUntil: 'load', timeout: 30_000 })
  await page.evaluate(async () => {
    await document.fonts.ready
    await Promise.race([
      Promise.all([...document.images].map((image) => image.decode().catch(() => {}))),
      new Promise((resolve) => setTimeout(resolve, 5_000)),
    ])
  })
  const path = resolve(root, 'preview/share.jpg')
  await page.screenshot({ path, type: 'jpeg', quality: 92, timeout: 60_000 })
  const { statSync } = await import('node:fs')
  console.log(`generated preview/share.jpg (${Math.round(statSync(path).size / 1024)} KB)`)
} finally {
  await browser.close()
  await rm(htmlPath, { force: true })
}
