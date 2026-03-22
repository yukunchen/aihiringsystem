import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('Job Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should create new job', async ({ page }) => {
    await page.click('text=Jobs');
    await page.click('text=Create JD');
    const jdPath = path.join(__dirname, '../fixtures/sample-jd.txt');
    const jdContent = fs.readFileSync(jdPath, 'utf-8');
    await page.fill('input[name="title"]', 'E2E Test Job');
    await page.fill('textarea[name="description"]', jdContent);
    // Wait for departments to load (sentinel element in JobCreatePage)
    await page.waitForSelector('text=Select department');
    await page.click('.ant-select-selector');
    await page.click('.ant-select-item:first-child');
    await page.click('button[type="submit"]');
    // Should navigate to job detail page
    await expect(page).toHaveURL(/.*\/jobs\/.+/);
  });

  test('should display job list', async ({ page }) => {
    await page.click('text=Jobs');
    await expect(page.locator('table')).toBeVisible();
  });

  test('should view job details', async ({ page }) => {
    await page.click('text=Jobs');
    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("View")').click();
      await expect(page.locator('h2')).toBeVisible();
    }
  });

  test('should update job status', async ({ page }) => {
    await page.click('text=Jobs');
    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("View")').click();
      // Check if status dropdown is available
      const statusTrigger = page.locator('text=Change status');
      if (await statusTrigger.isVisible()) {
        await statusTrigger.click();
        await page.click('[role="option"]:first-child');
        // Status should update
        await expect(page.locator('.ant-tag')).toBeVisible();
      }
    }
  });
});
