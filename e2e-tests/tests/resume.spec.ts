import { test, expect } from '@playwright/test';

test.describe('Resume Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
    await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should display resume list page', async ({ page }) => {
    await page.getByRole('link', { name: /resumes/i }).click();
    await expect(page.locator('table')).toBeVisible();
  });

  test('should navigate to upload page', async ({ page }) => {
    await page.getByRole('link', { name: /resumes/i }).click();
    const uploadBtn = page.getByRole('button', { name: /upload/i });
    if (await uploadBtn.isVisible()) {
      await uploadBtn.click();
      await expect(page).toHaveURL(/.*\/resumes\/upload.*/);
    }
  });

  test('should display batch upload option', async ({ page }) => {
    await page.getByRole('link', { name: /resumes/i }).click();
    const batchBtn = page.getByRole('button', { name: /batch/i });
    if (await batchBtn.isVisible()) {
      await batchBtn.click();
      await expect(page.locator('.ant-modal')).toBeVisible();
    }
  });
});
