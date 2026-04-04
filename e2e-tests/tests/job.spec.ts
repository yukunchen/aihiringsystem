import { test, expect } from '@playwright/test';

test.describe('Job Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
    await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should display job list page', async ({ page }) => {
    await page.getByRole('link', { name: /jobs/i }).click();
    await expect(page.locator('table')).toBeVisible();
  });

  test('should navigate to job creation page', async ({ page }) => {
    await page.getByRole('link', { name: /jobs/i }).click();
    const createBtn = page.getByRole('button', { name: /create|new|add/i });
    if (await createBtn.isVisible()) {
      await createBtn.click();
      await expect(page).toHaveURL(/.*\/jobs\/(new|create).*/);
    }
  });

  test('should view first job detail', async ({ page }) => {
    await page.getByRole('link', { name: /jobs/i }).click();
    const firstRow = page.locator('table tbody tr').first();
    if (await firstRow.isVisible()) {
      const viewBtn = firstRow.getByRole('button', { name: /view|detail/i });
      const rowLink = firstRow.locator('a').first();
      if (await viewBtn.isVisible()) {
        await viewBtn.click();
      } else if (await rowLink.isVisible()) {
        await rowLink.click();
      }
      await expect(page.locator('h2, h1')).toBeVisible();
    }
  });
});
