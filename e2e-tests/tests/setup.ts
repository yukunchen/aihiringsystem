import { test as setup, expect } from '@playwright/test';

const AUTH_FILE = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  await page.goto('/');
  await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
  await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  await page.context().storageState({ path: AUTH_FILE });
});

setup('cleanup test data', async ({ request }) => {
  if (process.env.TEST_SECRET) {
    await request.post('/api/test/cleanup', {
      headers: { 'X-Test-Secret': process.env.TEST_SECRET },
    });
  }
});
