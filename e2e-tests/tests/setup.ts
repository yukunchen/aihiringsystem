import { test as setup, expect } from '@playwright/test';

const AUTH_FILE = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  await page.goto('/');
  await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
  await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
  await page.getByRole('button', { name: /login/i }).click();
  await expect(page).toHaveURL(/.*\/(jobs|resumes).*/, { timeout: 15000 });
  await page.context().storageState({ path: AUTH_FILE });
});
