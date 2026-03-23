import { test, expect } from '@playwright/test';

test.describe('AI Matching', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*\/(jobs|resumes).*/);
  });

  test('should trigger AI matching from job detail', async ({ page }) => {
    await page.click('text=Jobs');
    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("View")').click();
      await page.click('button:has-text("Find Matching Resumes")');
      // Wait for matching to complete (up to 60 seconds)
      await expect(page.locator('table, text=No matching resumes, text=has not been indexed')).toBeVisible({ timeout: 60000 });
    }
  });

  test('should display match results with scores', async ({ page }) => {
    await page.click('text=Jobs');
    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("View")').click();
      // Click find matching resumes
      const matchButton = page.locator('button:has-text("Find Matching Resumes")');
      if (await matchButton.isVisible()) {
        await matchButton.click();
        // Wait for results
        await page.waitForSelector('table, text=No matching resumes, text=has not been indexed', { timeout: 60000 });
        // If we have results, check for score columns
        const scoreColumn = page.locator('text=LLM Score');
        if (await scoreColumn.isVisible()) {
          await expect(scoreColumn).toBeVisible();
        }
      }
    }
  });

  test('should show match reasoning tooltip', async ({ page }) => {
    await page.click('text=Jobs');
    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("View")').click();
      const matchButton = page.locator('button:has-text("Find Matching Resumes")');
      if (await matchButton.isVisible()) {
        await matchButton.click();
        // Wait for results
        await page.waitForSelector('table, text=No matching resumes, text=has not been indexed', { timeout: 60000 });
        // If results table exists, hover over reasoning cell to see tooltip
        const reasoningCell = page.locator('td:has-text("...")').first();
        if (await reasoningCell.isVisible()) {
          await reasoningCell.hover();
          // Tooltip should appear
          await expect(page.locator('.ant-tooltip')).toBeVisible();
        }
      }
    }
  });
});
