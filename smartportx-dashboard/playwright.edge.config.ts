import { defineConfig } from '@playwright/test'

// Local verification config: uses the preinstalled Microsoft Edge channel because
// the bundled Chromium CDN download is unavailable in this environment.
export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3100',
    headless: true,
    channel: 'msedge',
  },
  webServer: {
    command: 'npm run dev -- --port 3100 --strictPort',
    url: 'http://localhost:3100/login',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
