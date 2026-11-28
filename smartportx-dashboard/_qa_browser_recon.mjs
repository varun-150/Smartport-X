import { chromium } from 'playwright'
import fs from 'node:fs'

const OUT = 'C:/Users/akuri/AppData/Local/Temp/opencode/smartportx-qa/evidence'
fs.mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const consoleErrors = []
const failedRequests = []
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()) })
page.on('pageerror', e => consoleErrors.push('PAGEERROR: ' + String(e)))
page.on('requestfailed', r => failedRequests.push(`${r.method()} ${r.url()} :: ${r.failure()?.errorText}`))

const resp = await page.goto('http://localhost:3100/login', { waitUntil: 'networkidle', timeout: 30000 })
await page.screenshot({ path: `${OUT}/recon-login.png`, fullPage: true })

const info = await page.evaluate(() => ({
  title: document.title,
  headings: [...document.querySelectorAll('h1,h2,h3')].map(e => e.textContent.trim()),
  buttons: [...document.querySelectorAll('button')].map(e => ({ text: e.textContent.trim(), label: e.getAttribute('aria-label'), type: e.getAttribute('type') })),
  links: [...document.querySelectorAll('a')].map(e => ({ text: e.textContent.trim(), href: e.getAttribute('href') })),
  inputs: [...document.querySelectorAll('input')].map(e => ({ name: e.name, type: e.type, placeholder: e.placeholder, label: e.getAttribute('aria-label'), id: e.id })),
  text: document.body.innerText.slice(0, 3000)
}))

console.log(JSON.stringify({
  url: page.url(),
  status: resp?.status(),
  info,
  consoleErrors,
  failedRequests: failedRequests.slice(0, 20)
}, null, 2))
await browser.close()
