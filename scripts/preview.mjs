import { createServer } from 'node:http'
import { watch } from 'node:fs'
import { cp, mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve, extname, sep } from 'node:path'

// Local preview only. Rebuilds public files and refreshes open preview tabs.
const root = resolve('out')
const port = Number(process.env.PREVIEW_PORT ?? 4173)
const clients = new Set()
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon' }
const refresh = '<script>const previewEvents = new EventSource("/__preview/events"); previewEvents.addEventListener("refresh", () => location.reload());</script>'
let rebuilding = false
let pending = false
let timer

async function rebuild() {
  if (rebuilding) { pending = true; return }
  rebuilding = true
  try {
    const { renderPage, renderApiGuide } = await import('./render-pages.mjs?preview=' + Date.now())
    const index = JSON.parse(await readFile('history/index.json', 'utf8'))
    const dates = index.dates.map(entry => entry.date)
    await mkdir(resolve(root, 'archive'), { recursive: true })
    await cp('assets', resolve(root, 'assets'), { recursive: true, filter: file => !file.endsWith('.md') })
    await cp('history', resolve(root, 'history'), { recursive: true })
    let latest
    for (const entry of index.dates) {
      const record = JSON.parse(await readFile(resolve('history', entry.file), 'utf8'))
      await writeFile(resolve(root, 'archive', entry.date + '.html'), renderPage(record, { archive: true, dates }))
      await writeFile(resolve(root, 'archive', entry.date + '.json'), JSON.stringify(record, null, 2))
      latest = record
    }
    if (!latest) throw new Error('No local history records available')
    await writeFile(resolve(root, 'index.html'), renderPage(latest, { dates }))
    await writeFile(resolve(root, 'data.json'), JSON.stringify(latest, null, 2))
    await writeFile(resolve(root, 'api.html'), renderApiGuide(latest.date))
    for (const client of clients) client.write('event: refresh\ndata: ready\n\n')
    console.log('Local preview updated: ' + latest.date)
  } catch (error) {
    console.error('Preview rebuild failed:', error.message)
  } finally {
    rebuilding = false
    if (pending) { pending = false; schedule() }
  }
}

function schedule() {
  clearTimeout(timer)
  timer = setTimeout(rebuild, 180)
}

await rebuild()
const server = createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    if (pathname === '/__preview/events') {
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive' })
      res.write(': connected\n\n')
      clients.add(res)
      req.on('close', () => clients.delete(res))
      return
    }
    const file = resolve(root, '.' + (pathname.endsWith('/') ? pathname + 'index.html' : pathname))
    if (!file.startsWith(root + sep)) { res.writeHead(403).end(); return }
    let bytes = await readFile(file)
    if (extname(file) === '.html') bytes = Buffer.from(bytes.toString().replace('</body>', refresh + '</body>'))
    res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' }).end(bytes)
  } catch { res.writeHead(404).end('Not found') }
})
server.listen(port, '127.0.0.1', () => console.log(`Local preview: http://127.0.0.1:${port}/`))
const watchers = ['assets', 'scripts/render-pages.mjs', 'scripts/date-navigation.mjs', 'history/index.json', 'history/daily'].map(path => watch(path, schedule))
const heartbeat = setInterval(() => { for (const client of clients) client.write(': heartbeat\n\n') }, 15000)
function shutdown() {
  clearTimeout(timer)
  clearInterval(heartbeat)
  watchers.forEach(watcher => watcher.close())
  clients.forEach(client => client.end())
  server.close(() => process.exit(0))
}
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
