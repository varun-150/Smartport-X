import { defineConfig, devices } from '@playwright/test'

// Dedicated full-system validation config: serial execution to respect the
// identity login rate limit (5 attempts/window) and to keep shared browser
// evidence deterministic. Uses locally installed Microsoft Edge.
export default defineConfig({
  testDir: 'e2e',
  testMatch: 'full-system-validation.spec.ts',
  timeout: 60_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    ...devices['Desktop Chrome'],
    channel: 'msedge',
    // 5173 is in the notification service's default WebSocket allowed-origins list;
    // using it keeps the realtime channel in scope instead of producing 403s.
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure'
  },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173/login',
    reuseExistingServer: true,
    timeout: 120_000
  }
})
