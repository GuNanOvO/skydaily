import { createServer } from 'node:http'
import { readFile, mkdir, rm } from 'node:fs/promises'
import { resolve, extname, sep } from 'node:path'
import { spawnSync } from 'node:child_process'
import { chromium } from 'playwright'

const root = resolve('out')
const origin = 'http://127.0.0.1'
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.json': 'application/json' }

const watchdog = setTimeout(() => {
  console.error('preview timed out')
  process.exit(1)
}, 120_000)
watchdog.unref()

const server = createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    const file = resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname))
    if (!file.startsWith(root + sep)) { res.writeHead(403).end(); return }
    const bytes = await readFile(file)
    res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' }).end(bytes)
  } catch { res.writeHead(404).end() }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))

let browser
const temporary = resolve(root, 'preview/daily.png')
try {
  await mkdir(resolve(root, 'preview'), { recursive: true })
  browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1200, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce' })
  await page.route('**/*', route => {
    const url = route.request().url()
    if (url.startsWith(origin)) return route.continue()
    return route.abort()
  })
  await page.goto(`${origin}:${server.address().port}/`, { waitUntil: 'domcontentloaded', timeout: 30_000 })
  await page.addStyleTag({ content: '.season-guide{display:none}' })
  for (const image of await page.locator('main img').all()) {
    if (await image.isVisible()) await image.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {})
  }
  await page.evaluate(async () => {
    await document.fonts.ready
    await Promise.race([
      Promise.all([...document.images].map(image => image.decode().catch(() => {}))),
      new Promise(resolve => setTimeout(resolve, 5_000)),
    ])
  })
  await page.locator('main').screenshot({ path: temporary, animations: 'disabled', timeout: 60_000 })
  const encoded = spawnSync('python3', ['-c', 'from PIL import Image; import sys; Image.open(sys.argv[1]).convert("RGB").save(sys.argv[2], "WEBP", quality=90, method=6)', temporary, resolve(root, 'preview/daily.webp')], { stdio: 'inherit' })
  if (encoded.status !== 0) throw new Error('preview encoding failed')
  console.log('generated preview/daily.webp')
} finally {
  await rm(temporary, { force: true })
  await browser?.close()
  server.closeAllConnections?.()
  await new Promise(resolve => server.close(resolve))
  clearTimeout(watchdog)
}
