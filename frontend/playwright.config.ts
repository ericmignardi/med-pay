import { defineConfig, devices } from '@playwright/test';

/**
 * The suite runs against the `demo` profile stack from `docker compose up`, not against
 * `vite dev` with a mocked API. A spec that talks to a mock cannot prove FR-027 — the
 * whole point of `route-protection.spec.ts` is that the *server* rejects the call.
 *
 * `MEDPAY_BASE_URL` overrides the target so the same specs can run against a deployed
 * environment (Phase 10 exit criterion) without editing anything.
 */
const baseURL = process.env.MEDPAY_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],

  timeout: 45_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  // Waits on the backend through the Nginx proxy, so a green start means the whole
  // chain is up rather than just the static server.
  webServer: {
    command: 'docker compose up -d --wait',
    cwd: '..',
    url: `${baseURL}/actuator/health`,
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
