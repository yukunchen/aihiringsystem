import { test, expect } from '@playwright/test';
import path from 'path';

const FIXTURES = path.join(__dirname, '../fixtures');

// Tests 1 & 8A: Login flow (no storageState — test from scratch)
test.describe('Login flow', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('Test 1: login successfully → redirect to /jobs', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
    await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/\/jobs/, { timeout: 15000 });
  });

  test('Test 8A: wrong credentials → error alert', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('wrong_user');
    await page.getByPlaceholder('Password').fill('wrong_pass');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page.locator('.ant-alert-error')).toBeVisible({ timeout: 10000 });
  });
});

// Tests 2-5, 8B: Authenticated (use storageState from setup)
test.describe('Authenticated pages', () => {
  test('Test 2: resumes page loads', async ({ page }) => {
    await page.goto('/resumes');
    const table = page.locator('table');
    const empty = page.locator('.ant-empty-description');
    await expect(table.or(empty).first()).toBeVisible({ timeout: 10000 });
  });

  test('Test 3: upload resume successfully', async ({ page }) => {
    await page.goto('/resumes/upload');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(path.join(FIXTURES, '覃启航.pdf'));
    await expect(page.getByTestId('upload-btn')).toBeEnabled({ timeout: 5000 });
    await page.getByTestId('upload-btn').click();
    await expect(page).toHaveURL(/\/resumes$/, { timeout: 30000 });
  });

  test('Test 4: department dropdown non-empty + screenshot', async ({ page }) => {
    const deptResponse = page.waitForResponse(
      r => r.url().includes('/api/departments') && r.status() === 200,
    );
    await page.goto('/jobs/new');
    await deptResponse;

    // Open dropdown
    await page.getByPlaceholder('Select department').click();
    const dropdown = page.locator('.ant-select-dropdown:visible');
    await expect(dropdown).toBeVisible({ timeout: 5000 });

    // Verify options exist
    const options = dropdown.locator('.ant-select-item-option');
    const count = await options.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Screenshot for issue evidence
    const screenshot = await page.screenshot();
    await test.attach('department-dropdown', { body: screenshot, contentType: 'image/png' });
    // Also save to file for CI artifact pickup
    await page.screenshot({ path: 'test-results/department-dropdown.png' });
  });

  test('Test 5: required field validation errors', async ({ page }) => {
    await page.goto('/jobs/new');
    await page.getByRole('button', { name: /submit/i }).click();

    const errors = page.locator('.ant-form-item-explain-error');
    await expect(errors.first()).toBeVisible({ timeout: 5000 });
    const errorCount = await errors.count();
    expect(errorCount).toBeGreaterThanOrEqual(3);

    // URL should not change
    expect(page.url()).toContain('/jobs/new');
  });

  test('Test 8B: non-existent job → error + Back to Jobs', async ({ page }) => {
    await page.goto('/jobs/non-existent-id-12345');
    await expect(page.getByText('Failed to load job')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: /back to jobs/i })).toBeVisible();
  });
});
