import { test, expect } from '@playwright/test';

test.describe('AI Matching', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
    await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should trigger AI matching from job detail', async ({ page }) => {
    await page.getByRole('link', { name: /jobs/i }).click();
    const firstRow = page.locator('table tbody tr').first();
    if (await firstRow.isVisible()) {
      await firstRow.getByRole('button', { name: /view/i }).click();
      const matchButton = page.getByRole('button', { name: /find matching|match/i });
      if (await matchButton.isVisible()) {
        await matchButton.click();
        await expect(page.locator('table, text=No matching')).toBeVisible({ timeout: 60000 });
      }
    }
  });
});
