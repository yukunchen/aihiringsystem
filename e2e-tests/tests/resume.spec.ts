import { test, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('Resume Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should upload resume successfully', async ({ page }) => {
    await page.click('text=Resumes');
    await page.click('text=Upload Resume');
    const resumePath = path.join(__dirname, '../fixtures/sample-resume.pdf');
    await page.setInputFiles('input[type="file"]', resumePath);
    await page.click('[data-testid="upload-btn"]');
    await expect(page.locator('text=Resume uploaded successfully')).toBeVisible();
  });

  test('should display resume list', async ({ page }) => {
    await page.click('text=Resumes');
    await expect(page.locator('table')).toBeVisible();
  });

  test('should view resume details', async ({ page }) => {
    await page.click('text=Resumes');
    const firstResume = page.locator('table tbody tr').first();
    if (await firstResume.isVisible()) {
      await firstResume.click();
      await expect(page.locator('h2')).toBeVisible();
    }
  });
});
