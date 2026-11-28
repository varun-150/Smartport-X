/**
 * SMART PORTICS — real-world full-system browser validation.
 * Every test records an explicit PASS / FAIL / BLOCKED / NOT TESTED result.
 * Credentials come from the environment; no credential values are stored here.
 */
import { test, expect, type Page, type ConsoleMessage, type Request } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const EVIDENCE = process.env.QA_EVIDENCE_DIR || 'C:/Users/akuri/AppData/Local/Temp/opencode/smartportx-qa/evidence'
const RESULTS = path.join(EVIDENCE, 'browser-results.json')
fs.mkdirSync(EVIDENCE, { recursive: true })

const ADMIN_USER = process.env.QA_ADMIN_USER || 'admin'
const ADMIN_PASS = process.env.QA_ADMIN_PASS || ''
const VIEWER_USER = process.env.QA_VIEWER_USER || 'viewer'
const VIEWER_PASS = process.env.QA_VIEWER_PASS || ''
const API = process.env.QA_API_URL || 'http://localhost:8080'

// Login rate limit is 5/window, so sessions are acquired once via API and reused.
type Session = { access: string; refresh: string; user: any }
let adminSession: Session
let viewerSession: Session

type Status = 'PASS' | 'FAIL' | 'BLOCKED' | 'NOT TESTED'
const results: Array<{ test: string; status: Status; detail: string; screenshot?: string }> = []

function record(testName: string, status: Status, detail: string, screenshot?: string) {
  const entry = { test: testName, status, detail, screenshot }
  let all: typeof results = []
  try { all = JSON.parse(fs.readFileSync(RESULTS, 'utf8')) } catch { all = [] }
  all.push(entry)
  fs.writeFileSync(RESULTS, JSON.stringify(all, null, 2))
}

function attachDiagnostics(page: Page) {
  const consoleErrors: string[] = []
  const failedRequests: string[] = []
  const badResponses: string[] = []
  page.on('response', r => {
    if (r.status() >= 400 && !r.url().includes('/ws') && !r.url().includes('sockjs')) {
      badResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`)
    }
  })
  page.on('console', (m: ConsoleMessage) => {
    if (m.type() !== 'error') return
    const text = m.text()
    // Known benign Chromium/Edge policy notice emitted by Vite's HMR client.
    if (text.includes('Permissions policy violation: unload')) return
    consoleErrors.push(text.slice(0, 300))
  })
  page.on('pageerror', e => consoleErrors.push('PAGEERROR: ' + String(e).slice(0, 300)))
  page.on('requestfailed', (r: Request) => {
    const url = r.url()
    if (url.includes('/ws') || url.includes('sockjs')) return // expected when broker channel is idle
    failedRequests.push(`${r.method()} ${url} :: ${r.failure()?.errorText}`)
  })
  return { consoleErrors, failedRequests, badResponses }
}

async function loginUI(page: Page, user: string, pass: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(user)
  await page.getByLabel('Password').fill(pass)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 20000 })
  await expect(page.getByRole('heading', { name: 'Terminal Overview' })).toBeVisible({ timeout: 20000 })
}

async function seedSession(page: Page, session: Session) {
  // Seed once (not via addInitScript) so a simulated 401 can actually clear the
  // session and redirect; an init script would re-inject the token on every navigation.
  await page.goto('/login')
  await page.evaluate((s: Session) => {
    sessionStorage.setItem('smartportx.access', s.access)
    sessionStorage.setItem('smartportx.refresh', s.refresh)
    sessionStorage.setItem('smartportx.user', JSON.stringify(s.user))
  }, session)
  await page.goto('/dashboard')
  try {
    await expect(page.getByRole('heading', { name: 'Terminal Overview' })).toBeVisible({ timeout: 10000 })
  } catch {
    // Rare cold-start race: the SPA can mount before the seeded session is restored.
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Terminal Overview' })).toBeVisible({ timeout: 20000 })
  }
}

// Tests are independent; only the user-journey describe block is serial.

test.beforeAll(async ({ request }) => {
  if (!ADMIN_PASS || !VIEWER_PASS) {
    throw new Error('QA_ADMIN_PASS and QA_VIEWER_PASS must be provided by the validation harness')
  }
  for (const [cred, key] of [[{ u: ADMIN_USER, p: ADMIN_PASS }, 'admin'], [{ u: VIEWER_USER, p: VIEWER_PASS }, 'viewer']] as const) {
    const r = await request.post(`${API}/api/v1/auth/login`, { data: { username: cred.u, password: cred.p } })
    const b: any = await r.json()
    const s: Session = { access: b.accessToken, refresh: b.refreshToken, user: b.user }
    if (key === 'admin') adminSession = s; else viewerSession = s
  }
})

test.describe('A. Route coverage — authenticated admin', () => {
  let ids: { container: string; vessel: string; user: string } = { container: '', vessel: '', user: '' }

  test.beforeAll(async ({ request }) => {
    const auth = { Authorization: `Bearer ${adminSession.access}` }
    const c = await request.get(`${API}/api/v1/containers?page=1&size=1`, { headers: auth })
    const v = await request.get(`${API}/api/v1/vessels`, { headers: auth })
    const u = await request.get(`${API}/api/v1/users?page=1&size=1`, { headers: auth })
    const cj = await c.json(); const vj = await v.json(); const uj = await u.json()
    const first = (x: any) => {
      const d = x?.data ?? x
      const arr = Array.isArray(d) ? d : (d?.items ?? d?.content ?? d?.results ?? [])
      return arr[0]?.id ?? arr[0]?.containerId ?? arr[0]?.vesselId ?? ''
    }
    ids = { container: first(cj), vessel: first(vj), user: first(uj) }
    console.log('DYNAMIC_IDS=' + JSON.stringify(ids))
  })

  test.beforeEach(async ({ page }) => {
    await seedSession(page, adminSession)
  })

  const routes: Array<[string, string, string]> = [
    ['dashboard', '/dashboard', 'Terminal Overview'],
    ['containers', '/containers', 'Container Register'],
    ['yard', '/yard', 'Stacking Operations'],
    ['yard-equipment', '/yard/equipment', 'Equipment'],
    ['gate', '/gate', 'Appointments'],
    ['gate-book', '/gate/book', 'Book appointment'],
    ['vessels', '/vessels', 'Vessels'],
    ['notifications', '/notifications', 'Delivery preferences & live feed'],
    ['testimonials', '/testimonials', 'Customer testimonials'],
    ['admin-users', '/admin/users', 'Users'],
    ['admin-users-new', '/admin/users/new', 'Create user'],
    ['system', '/system', 'Runtime console'],
    ['forbidden', '/forbidden', 'Access forbidden']
  ]

  for (const [name, route, heading] of routes) {
    test(`route:${name}`, async ({ page }) => {
      const d = attachDiagnostics(page)
      const shot = path.join(EVIDENCE, `route-${name}.png`)
      await page.goto(route)
      const title = heading === 'Access forbidden'
        ? page.getByText('Access forbidden', { exact: true }).first()
        : page.getByRole('heading', { name: heading, exact: false }).first()
      await expect(title).toBeVisible({ timeout: 20000 })
      await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
      await page.screenshot({ path: shot, fullPage: true })
      const ok = d.consoleErrors.length === 0 && d.failedRequests.length === 0 && d.badResponses.length === 0
      record(
        `Route ${route} — render + data + console/network`,
        ok ? 'PASS' : 'FAIL',
        `heading="${heading}"; consoleErrors=${d.consoleErrors.length}; failedRequests=${d.failedRequests.length}; httpErrors=${d.badResponses.length}${d.badResponses.length ? '; urls=' + d.badResponses.slice(0, 5).join(' | ') : ''}`,
        shot
      )
      expect(await title.isVisible()).toBeTruthy()
    })
  }

  test('route:dynamic-detail-pages', async ({ page }) => {
    const d = attachDiagnostics(page)
    const missing: string[] = []
    if (!ids.container) missing.push('container')
    if (!ids.vessel) missing.push('vessel')
    if (!ids.user) missing.push('user')
    if (missing.length) {
      record('Dynamic detail routes', 'BLOCKED', `No dynamic ID resolved for: ${missing.join(', ')}`)
      test.skip(true, 'dynamic IDs unavailable')
      return
    }
    const shot = path.join(EVIDENCE, 'route-dynamic-details.png')
    await page.goto(`/containers/${ids.container}`)
    await expect(page.getByRole('heading', { name: 'Container detail' })).toBeVisible({ timeout: 15000 })
    await page.goto(`/vessels/${ids.vessel}`)
    await expect(page.getByRole('heading', { name: 'Vessel detail' })).toBeVisible({ timeout: 15000 })
    await page.goto(`/admin/users/${ids.user}/edit`)
    await expect(page.getByRole('heading', { name: 'Edit user' })).toBeVisible({ timeout: 15000 })
    await page.screenshot({ path: shot, fullPage: true })
    const ok = d.consoleErrors.length === 0 && d.failedRequests.length === 0
    record('Dynamic detail routes (container/vessel/user edit)', ok ? 'PASS' : 'FAIL',
      `ids=${JSON.stringify(ids)}; consoleErrors=${d.consoleErrors.length}; failedRequests=${d.failedRequests.length}`, shot)
    expect(ok).toBeTruthy()
  })

  test('route:404-not-found', async ({ page }) => {
    const d = attachDiagnostics(page)
    const shot = path.join(EVIDENCE, 'route-404.png')
    await page.goto('/this-route-does-not-exist-qa')
    await expect(page.getByText('Page not found')).toBeVisible({ timeout: 15000 })
    await page.screenshot({ path: shot, fullPage: true })
    record('Unmatched route → 404 page', d.consoleErrors.length === 0 ? 'PASS' : 'FAIL',
      `consoleErrors=${d.consoleErrors.length}`, shot)
  })
})

test.describe('B. UI states, interactions and accessibility', () => {
  test.beforeEach(async ({ page }) => { await seedSession(page, adminSession) })

  test('slow-network loading state', async ({ page }) => {
    await page.route('**/api/v1/containers?**', async route => {
      await new Promise(r => setTimeout(r, 1800))
      await route.continue()
    })
    const shot = path.join(EVIDENCE, 'state-loading.png')
    await page.goto('/containers')
    const spinner = page.locator('[role=status]').filter({ hasText: 'Loading containers' })
    const visible = await spinner.isVisible().catch(() => false)
    await page.screenshot({ path: shot })
    record('Slow API → loading indicator', visible ? 'PASS' : 'FAIL',
      visible ? 'Spinner "Loading containers…" observed during 1.8s delayed response' : 'No spinner observed; page may render spinner too briefly', shot)
    expect(visible).toBeTruthy()
  })

  test('API 500 → error panel + retry recovery', async ({ page }) => {
    let fail = true
    await page.route('**/api/v1/containers?**', async route => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Internal Server Error', detail: 'QA injected failure' }) })
      else await route.continue()
    })
    const shotFail = path.join(EVIDENCE, 'state-api-500.png')
    await page.goto('/containers')
    const alert = page.locator('[role=alert].error-panel')
    await expect(alert).toBeVisible({ timeout: 15000 })
    await page.screenshot({ path: shotFail, fullPage: true })
    fail = false
    await alert.getByRole('button', { name: 'Retry' }).click()
    await expect(page.locator('table.data')).toBeVisible({ timeout: 15000 })
    const shotOk = path.join(EVIDENCE, 'state-retry-recovered.png')
    await page.screenshot({ path: shotOk, fullPage: true })
    record('HTTP 500 → actionable error + Retry recovers', 'PASS',
      'ErrorPanel with Retry displayed; retry rendered populated table', shotFail)
  })

  test('HTTP status matrix → usable error feedback', async ({ page }) => {
    const statuses = [400, 401, 403, 404, 429, 500]
    for (const status of statuses) {
      await page.unrouteAll({ behavior: 'ignoreErrors' })
      await page.route('**/api/v1/containers?**', async route => {
        await route.fulfill({ status, contentType: 'application/problem+json',
          headers: { 'retry-after': '2' },
          body: JSON.stringify({ title: `QA injected ${status}`, detail: 'Simulated upstream failure' }) })
      })
      if (status === 401) {
        // Expire the refresh token too, otherwise the client silently renews and retries.
        await page.route('**/api/v1/auth/refresh', route =>
          route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }))
      }
      await page.goto('/containers')
      const alert = page.locator('[role=alert], [role=status].degraded-banner')
      const count = await alert.count()
      const text = count ? (await alert.first().innerText()).slice(0, 120).replace(/\n/g, ' ') : '(no feedback element)'
      const shot = path.join(EVIDENCE, `state-http-${status}.png`)
      await page.screenshot({ path: shot, fullPage: true })
      if (status === 401) {
        // Verified design: http.ts attempts one silent refresh; on failure it clears the
        // session and AuthProvider redirects to /login instead of showing an error panel.
        await expect(page).toHaveURL(/\/login/, { timeout: 15000 })
        record('Failure simulation HTTP 401 → session-expiry redirect', 'PASS',
          '401 + failed refresh triggered session clear and redirect to /login (documented design)', shot)
        // Restore the seeded session so the remaining status codes are exercised in-context.
        await page.unrouteAll({ behavior: 'ignoreErrors' })
        await seedSession(page, adminSession)
        continue
      }
      record(`Failure simulation HTTP ${status} → UI feedback`, count > 0 ? 'PASS' : 'FAIL', text, shot)
      expect(count, `no feedback for ${status}`).toBeGreaterThan(0)
    }
  })

  test('temporary network loss → network-aware error', async ({ page }) => {
    await page.route('**/api/v1/containers?**', route => route.abort('failed'))
    await page.goto('/containers')
    const alert = page.locator('[role=alert].error-panel')
    await expect(alert).toBeVisible({ timeout: 15000 })
    const text = await alert.innerText()
    const shot = path.join(EVIDENCE, 'state-network-loss.png')
    await page.screenshot({ path: shot, fullPage: true })
    const networkNote = text.toLowerCase().includes('unreachable') || text.toLowerCase().includes('backend')
    record('Temporary network loss → meaningful message', networkNote ? 'PASS' : 'FAIL', text.slice(0, 200).replace(/\n/g, ' '), shot)
    expect(networkNote).toBeTruthy()
  })

  test('empty state via impossible filter', async ({ page }) => {
    const shot = path.join(EVIDENCE, 'state-empty-filter.png')
    await page.goto('/containers')
    await expect(page.locator('table.data')).toBeVisible({ timeout: 15000 })
    await page.getByPlaceholder('e.g. MAERSK').fill('ZZZ_NO_SUCH_OWNER_QA')
    await page.getByRole('button', { name: 'Apply' }).click()
    await expect(page.getByText('No containers match these filters.')).toBeVisible({ timeout: 15000 })
    await page.screenshot({ path: shot, fullPage: true })
    record('Filter with no matches → empty state', 'PASS', 'EmptyState rendered for impossible owner filter', shot)

    await page.getByRole('button', { name: 'Reset' }).click()
    await expect(page.locator('table.data')).toBeVisible({ timeout: 15000 })
    record('Reset filters → data restored', 'PASS', 'Reset button cleared filter and reloaded table')
  })

  test('pagination controls', async ({ page }) => {
    const shot = path.join(EVIDENCE, 'state-pagination.png')
    await page.goto('/containers')
    await expect(page.locator('table.data')).toBeVisible({ timeout: 15000 })
    const next = page.getByRole('button', { name: 'Next page' })
    const indicator = page.locator('.mono-gap').filter({ hasText: /^p\./ }).first()
    const before = await indicator.innerText().catch(() => '')
    const enabled = await next.isEnabled()
    if (!enabled) {
      record('Pagination next-page', 'BLOCKED', `Next page disabled — dataset fits one page (indicator ${before})`)
      return
    }
    await next.click()
    await page.waitForTimeout(1000)
    const after = await indicator.innerText().catch(() => '')
    await page.screenshot({ path: shot, fullPage: true })
    const ok = Boolean(after) && after !== before
    record('Pagination next-page', ok ? 'PASS' : 'FAIL',
      `indicator before=${before} after=${after}`, shot)
    expect(ok).toBeTruthy()
  })

  test('modal open, close button and Escape', async ({ page }) => {
    await page.goto('/gate')
    const table = page.locator('table.data')
    await expect(table.or(page.getByText('No appointments match the current filters.'))).toBeVisible({ timeout: 15000 })
    if (!(await table.count())) {
      record('Gate modal interactions', 'BLOCKED',
        'No appointment rows exist in the current dataset — confirm modal has no trigger (seed data has no appointments)')
      return
    }
    const confirm = page.getByRole('button', { name: 'Confirm', exact: true }).first()
    if (!(await confirm.count())) {
      record('Gate modal interactions', 'BLOCKED', 'No PENDING appointment available to open confirm modal')
      return
    }
    await confirm.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    const shot = path.join(EVIDENCE, 'state-modal.png')
    await page.screenshot({ path: shot })
    await dialog.getByRole('button', { name: 'Close' }).click()
    await expect(dialog).toBeHidden()
    await confirm.click()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    record('Gate modal open / Close button / Escape', 'PASS', 'Dialog opened and closed by both mechanisms', shot)
  })

  test('form validation — native required fields', async ({ page }) => {
    const shot = path.join(EVIDENCE, 'state-form-validation.png')
    await page.goto('/gate/book')
    await expect(page.getByRole('heading', { name: 'Book appointment' })).toBeVisible()
    const invalid = await page.locator('input:invalid').count()
    const submitDisabled = await page.getByRole('button', { name: 'Book appointment' }).isDisabled()
    await page.screenshot({ path: shot, fullPage: true })
    const ok = invalid > 0 && submitDisabled
    record('Gate booking form required-field validation', ok ? 'PASS' : 'FAIL',
      `Native :invalid fields on empty form = ${invalid}; submit disabled until container supplied = ${submitDisabled}`, shot)
    expect(ok).toBeTruthy()
  })

  test('browser refresh preserves authenticated session and route', async ({ page }) => {
    await page.goto('/containers')
    await expect(page.getByRole('heading', { name: 'Container Register' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Container Register' })).toBeVisible({ timeout: 15000 })
    const user = await page.evaluate(() => sessionStorage.getItem('smartportx.user'))
    record('F5 refresh → session restored from sessionStorage', user ? 'PASS' : 'FAIL',
      `sessionStorage user present=${Boolean(user)}; route retained after reload`)
    expect(user).toBeTruthy()
  })

  test('responsive layout at 390x844', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const shot = path.join(EVIDENCE, 'state-responsive-mobile.png')
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Terminal Overview' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    await page.screenshot({ path: shot, fullPage: true })
    record('Responsive 390x844 — no horizontal overflow', overflow <= 2 ? 'PASS' : 'FAIL',
      `horizontalOverflowPx=${overflow}`, shot)
    expect(overflow).toBeLessThanOrEqual(2)
  })

  test('accessibility basics — names, labels, landmarks', async ({ page }) => {
    await page.goto('/containers')
    await expect(page.locator('table.data')).toBeVisible({ timeout: 15000 })
    const info = await page.evaluate(() => {
      const unnamed = [...document.querySelectorAll('button')].filter(b =>
        !(b.getAttribute('aria-label') || b.textContent?.trim() || b.getAttribute('title'))).length
      const unlabeledInputs = [...document.querySelectorAll('input')].filter(i => {
        if (i.getAttribute('aria-label') || i.getAttribute('aria-labelledby')) return false
        if (i.id && document.querySelector(`label[for="${i.id}"]`)) return false
        return i.closest('label') === null
      }).length
      return { unnamedButtons: unnamed, unlabeledInputs, tables: document.querySelectorAll('table').length,
        nav: document.querySelectorAll('nav[aria-label]').length, h1: document.querySelectorAll('h1').length }
    })
    const ok = info.unnamedButtons === 0 && info.unlabeledInputs === 0 && info.h1 === 1
    record('Accessibility basics (button names, input labels, h1)', ok ? 'PASS' : 'FAIL', JSON.stringify(info))
    expect(ok).toBeTruthy()
  })
})

test.describe('C. Full user journeys', () => {
  test.describe.configure({ mode: 'serial' })
  test('viewer role is redirected away from admin area', async ({ page }) => {
    const d = attachDiagnostics(page)
    await seedSession(page, viewerSession)
    await page.goto('/admin/users')
    await expect(page).toHaveURL(/\/forbidden/, { timeout: 15000 })
    await expect(page.getByText('Access forbidden')).toBeVisible()
    const shot = path.join(EVIDENCE, 'journey-viewer-forbidden.png')
    await page.screenshot({ path: shot, fullPage: true })
    record('Login → role verification → protected page denied', 'PASS',
      `viewer redirected to /forbidden; consoleErrors=${d.consoleErrors.length}`, shot)
  })

  test('authenticated deep-link after login', async ({ page }) => {
    await page.goto('/containers')
    await expect(page).toHaveURL(/\/login/)
    await page.getByLabel('Username').fill(ADMIN_USER)
    await page.getByLabel('Password').fill(ADMIN_PASS)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 20000 })
    const landed = new URL(page.url()).pathname
    const shot = path.join(EVIDENCE, 'journey-deeplink.png')
    await page.screenshot({ path: shot })
    record('Unauthenticated deep link → login → original route', landed === '/containers' ? 'PASS' : 'FAIL',
      `requested /containers, landed ${landed}; root cause: RequireAuth stores state.from as string, LoginPage reads state.from.pathname`, shot)
  })

  test('testimonial create → DB/UI persistence → delete', async ({ page, request }) => {
    const marker = `QA-${Date.now()}`
    const token = adminSession.access
    const auth = { Authorization: `Bearer ${token}` }
    let createdId = ''
    try {
      await seedSession(page, adminSession)
      await page.goto('/testimonials')
      await page.getByRole('button', { name: 'Add testimonial' }).click()
      const dialog = page.getByRole('dialog')
      await dialog.getByLabel('Name').fill(marker)
      await dialog.getByLabel('Message').fill('Automated real-world validation record — safe to delete.')
      await dialog.getByLabel('Rating').selectOption('5')
      await dialog.getByRole('button', { name: 'Add testimonial' }).click()
      await expect(page.getByText(marker)).toBeVisible({ timeout: 15000 })
      const list = await request.get(`${API}/api/v1/testimonials`, { headers: auth })
      const listBody: any = await list.json()
      const arr: any[] = Array.isArray(listBody) ? listBody : (listBody?.data ?? listBody?.items ?? [])
      const found = arr.find((t: any) => t.name === marker)
      createdId = found?.id ?? ''
      const shot = path.join(EVIDENCE, 'journey-testimonial-created.png')
      await page.screenshot({ path: shot, fullPage: true })
      await page.reload()
      await expect(page.getByText(marker)).toBeVisible({ timeout: 15000 })
      record('Create testimonial → API listing → UI refresh persistence',
        createdId && (await page.getByText(marker).count()) > 0 ? 'PASS' : 'FAIL',
        `marker=${marker}; id=${createdId || 'not-in-API'}`, shot)

      page.once('dialog', d => d.accept())
      const row = page.locator('tr', { hasText: marker })
      await row.getByRole('button', { name: 'Delete' }).click()
      await expect(page.getByText(marker)).toHaveCount(0, { timeout: 15000 })
      record('Delete testimonial → removed from UI', 'PASS', `deleted ${marker}`)
    } catch (e) {
      record('Testimonial CRUD journey', 'FAIL', String(e).slice(0, 300))
      throw e
    } finally {
      if (createdId) {
        await request.delete(`${API}/api/v1/testimonials/${createdId}`, { headers: auth })
      }
    }
  })

  test('invalid container number is rejected without creating a record', async ({ request }) => {
    const token = adminSession.access
    const auth = { Authorization: `Bearer ${token}` }
    const before = await request.get(`${API}/api/v1/containers?page=1&size=1`, { headers: auth })
    const beforeBody: any = await before.json()
    const beforeTotal = beforeBody?.total ?? beforeBody?.data?.total ?? -1
    const bad = await request.post(`${API}/api/v1/containers`, {
      headers: auth,
      data: { containerNumber: 'MSKU0000000', type: '20DC', owner: 'QA', weightKg: 1000 }
    })
    const after = await request.get(`${API}/api/v1/containers?page=1&size=1`, { headers: auth })
    const afterBody: any = await after.json()
    const afterTotal = afterBody?.total ?? afterBody?.data?.total ?? -1
    const ok = bad.status() >= 400 && bad.status() < 500 && beforeTotal === afterTotal
    record('Invalid ISO 6346 container rejected, no DB mutation', ok ? 'PASS' : 'FAIL',
      `POST status=${bad.status()}; total before=${beforeTotal} after=${afterTotal}`)
    expect(ok).toBeTruthy()
  })

  test('concurrent duplicate create yields at most one success', async ({ request }) => {
    const token = adminSession.access
    const auth = { Authorization: `Bearer ${token}` }
    const number = 'QATU' + String(Date.now()).slice(-7)
    const payload = { containerNumber: number, type: '20DC', owner: 'QA', weightKg: 1000 }
    const responses = await Promise.all([
      request.post(`${API}/api/v1/containers`, { headers: auth, data: payload }),
      request.post(`${API}/api/v1/containers`, { headers: auth, data: payload })
    ])
    const codes = responses.map(r => r.status())
    const ok = codes.filter(c => c >= 200 && c < 300).length <= 1
    record('Concurrent duplicate create → single success', ok ? 'PASS' : 'FAIL', `statuses=${JSON.stringify(codes)}`)
    expect(ok).toBeTruthy()
  })
})
