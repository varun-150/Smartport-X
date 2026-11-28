import { chromium } from 'playwright'

const API = 'http://localhost:8080'
const creds = { username: 'admin', password: 'Admin@12345' }
const login = await fetch(`${API}/api/v1/auth/login`, {
  method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(creds)
})
const body = await login.json()
const session = { access: body.accessToken, refresh: body.refreshToken, user: body.user }

const browser = await chromium.launch({ channel: 'msedge', headless: true })
for (const route of ['/dashboard', '/yard', '/yard/equipment']) {
  const page = await browser.newPage()
  const logs = []
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`CONSOLE[${m.type()}] ${m.text()}`) })
  page.on('pageerror', e => logs.push(`PAGEERROR ${String(e)}`))
  page.on('response', r => { if (r.status() >= 400 && !r.url().includes('/ws')) logs.push(`HTTP ${r.status()} ${r.url()}`) })
  await page.addInitScript(s => {
    sessionStorage.setItem('smartportx.access', s.access)
    sessionStorage.setItem('smartportx.refresh', s.refresh)
    sessionStorage.setItem('smartportx.user', JSON.stringify(s.user))
  }, session)
  await page.goto(`http://localhost:3100${route}`, { waitUntil: 'load' })
  await page.waitForTimeout(3500)
  const h1 = await page.locator('h1').allTextContents()
  const url = page.url()
  console.log(`\n=== ${route} -> ${url} ===`)
  console.log(`H1s: ${JSON.stringify(h1)}`)
  logs.forEach(l => console.log(l.slice(0, 400)))
  await page.close()
}
await browser.close()
