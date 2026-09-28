import { defineConfig, devices } from '@playwright/test';
import { loadEnvFile } from 'node:process';
import { fileURLToPath } from 'node:url';

loadEnvFile(fileURLToPath(new URL('../.local/e2e.env', import.meta.url)));
// Deliberately fixed to the isolated local stack: no production target argument.
export default defineConfig({
  testDir: './tests', workers: 1, fullyParallel: false, retries: 0,
  timeout: 90000, expect: { timeout: 15000 },
  reporter: [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/junit.xml' }]],
  use: {
    baseURL: 'http://localhost:14200', trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env.BRICO_E2E_BROWSER_PATH
      ? { executablePath: process.env.BRICO_E2E_BROWSER_PATH } : {},
  },
  projects: [
    { name: 'mobile', use: { ...devices['Pixel 7'], defaultBrowserType: 'chromium' }, testMatch: /purchase\.spec\.mjs/ },
    { name: 'desktop', use: { viewport: { width: 1280, height: 900 } }, testMatch: /operations\.spec\.mjs/ },
  ],
});
