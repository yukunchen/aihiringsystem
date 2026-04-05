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
    await expect(page.locator('table')).toBeVisible();
    const viewBtn = page.locator('table tbody tr').first().getByRole('button', { name: /view/i });
    if (await viewBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await viewBtn.click();
      const matchButton = page.getByRole('button', { name: /find matching|match/i });
      if (await matchButton.isVisible({ timeout: 5000 }).catch(() => false)) {
        await matchButton.click();
        await expect(page.locator('table, text=No matching')).toBeVisible({ timeout: 60000 });
      }
    }
  });
});
