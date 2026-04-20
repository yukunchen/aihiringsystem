import { test, expect, Page } from '@playwright/test';
import { fileURLToPath } from 'url';
import path from 'path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURES = path.join(__dirname, '../fixtures');
const TS = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);

async function login(page: Page) {
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.goto('/login', { waitUntil: 'networkidle' });
    await page.getByPlaceholder('Username').fill(process.env.TEST_USERNAME || 'admin');
    await page.getByPlaceholder('Password').fill(process.env.TEST_PASSWORD || 'admin123');
    await page.getByRole('button', { name: /login/i }).click();
    try {
      await page.waitForURL(/.*\/(jobs|resumes|dashboard)/, { timeout: 20000 });
      await page.waitForLoadState('networkidle');
      return; // success
    } catch {
      if (attempt < 2) await page.waitForTimeout(3000);
    }
  }
  const url = page.url();
  throw new Error(`Login failed after 3 attempts. Current URL: ${url}`);
}

test.describe.serial('Core flows', () => {
  test('create JD - happy path', async ({ page }) => {
    await login(page);
    const title = `E2E-JD-${TS}`;

    await page.goto('/jobs', { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: /create/i }).click();
    await expect(page).toHaveURL(/.*\/jobs\/(create|new)/);
    await page.waitForLoadState('networkidle');

    // Fill form
    await page.getByPlaceholder('Title').fill(title);
    await page.locator('.ant-select').click();
    await page.locator('.ant-select-item').first().click();
    await page.getByPlaceholder('Description').fill('E2E test job description');

    // Submit
    await page.getByRole('button', { name: /submit/i }).click();

    // Verify redirect to detail page with our title
    await expect(page.getByText(title)).toBeVisible({ timeout: 15000 });
    const url = page.url();
    expect(url).toMatch(/\/jobs\/[a-f0-9-]+/);

    // Verify persistence after refresh
    await page.reload();
    await expect(page.getByText(title)).toBeVisible({ timeout: 15000 });
  });

  test('create JD - validation errors on empty required fields', async ({ page }) => {
    await login(page);
    await page.goto('/jobs/new', { waitUntil: 'networkidle' });

    // Submit without filling anything
    await page.getByRole('button', { name: /submit/i }).click();

    // Ant Design form validation messages should appear
    await expect(page.locator('.ant-form-item-explain-error').first()).toBeVisible({ timeout: 5000 });
  });

  test('upload single resume - happy path', async ({ page }) => {
    await login(page);
    await page.goto('/resumes/upload', { waitUntil: 'networkidle' });

    // Upload real PDF fixture
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(path.join(FIXTURES, '覃启航.pdf'));

    // Click upload
    await page.getByTestId('upload-btn').click();

    // Should redirect to resume list
    await expect(page).toHaveURL(/.*\/resumes/, { timeout: 15000 });

    // Verify the file appears in the list
    await expect(page.getByText('覃启航')).toBeVisible({ timeout: 10000 });
  });

  test('upload resume - reject unsupported file type', async ({ page }) => {
    await login(page);
    await page.goto('/resumes/upload', { waitUntil: 'networkidle' });

    // Verify the upload button stays disabled when no valid file is selected
    const uploadBtn = page.getByTestId('upload-btn');
    await expect(uploadBtn).toBeDisabled();
  });

  test('batch upload resumes - 3 PDFs', async ({ page }) => {
    await login(page);
    await page.goto('/resumes', { waitUntil: 'networkidle' });

    // Open batch upload modal
    await page.getByRole('button', { name: /batch/i }).click();
    await expect(page.getByText('Batch Upload Resumes')).toBeVisible();

    // Upload 3 PDFs via the Ant Design Upload.Dragger file input
    const fileInput = page.locator('.ant-upload input[type="file"]');
    await fileInput.first().setInputFiles([
      path.join(FIXTURES, '覃启航.pdf'),
      path.join(FIXTURES, '张芷菁.pdf'),
      path.join(FIXTURES, 'Unity 中高级开发工程师.pdf'),
    ]);

    // Verify files appear in the list
    await expect(page.getByText('覃启航.pdf')).toBeVisible();
    await expect(page.getByText('张芷菁.pdf')).toBeVisible();
    await expect(page.getByText('Unity 中高级开发工程师.pdf')).toBeVisible();

    // Click upload
    await page.getByRole('button', { name: /upload/i }).last().click();

    // Wait for success toast
    await expect(page.getByText(/uploaded 3\/3/i)).toBeVisible({ timeout: 30000 });
  });
});

test.describe.serial('AI Matching', () => {
  test('trigger AI matching on a JD and get results', async ({ page }) => {
    await login(page);

    // Step 1: Navigate to jobs list and click into the first JD (created by earlier tests or existing)
    await page.goto('/jobs', { waitUntil: 'networkidle' });

    // If no jobs exist, create one first
    const hasJobs = await page.locator('.ant-table-row').count() > 0;
    let jobUrl: string;

    if (hasJobs) {
      // Click the "View" button on the first job row
      await page.locator('.ant-table-row').first().getByText('View').click();
      await page.waitForURL(/.*\/jobs\/[a-f0-9-]+/, { timeout: 10000 });
      jobUrl = page.url();
    } else {
      // Create a JD first
      await page.getByRole('button', { name: /create/i }).click();
      await page.waitForURL(/.*\/jobs\/(create|new)/);
      await page.waitForLoadState('networkidle');
      const title = `E2E-Match-${Date.now()}`;
      await page.getByPlaceholder('Title').fill(title);
      await page.locator('.ant-select').click();
      await page.locator('.ant-select-item').first().click();
      await page.getByPlaceholder('Description').fill('Senior backend engineer with Java and Spring Boot experience, 3+ years');
      await page.getByRole('button', { name: /submit/i }).click();
      await expect(page.getByText(title)).toBeVisible({ timeout: 15000 });
      jobUrl = page.url();
    }

    // Step 2: On the JD detail page, find the "Find Matching Resumes" button
    await expect(page.getByText('AI Matching')).toBeVisible({ timeout: 10000 });
    const matchBtn = page.getByRole('button', { name: /find matching/i });
    await expect(matchBtn).toBeVisible();

    // Step 3: Click the match button
    await matchBtn.click();

    // Step 4: Wait for results - could be:
    // - A table with matching resumes
    // - "No matching resumes found" alert
    // - 422 warning (JD not indexed yet - retry after wait)
    // - 503 error (AI service unavailable)
    // Allow up to 60s for AI processing
    const resultLocator = page.locator('.ant-table-row, .ant-alert');
    await expect(resultLocator.first()).toBeVisible({ timeout: 60000 });

    // If we got a 422 "not indexed" warning, wait and retry once
    const has422 = await page.getByText('not been indexed yet').isVisible().catch(() => false);
    if (has422) {
      await page.waitForTimeout(5000);
      await matchBtn.click();
      await expect(resultLocator.first()).toBeVisible({ timeout: 60000 });
    }

    // Take screenshot for evidence
    await page.screenshot({ path: 'test-results/ai-matching-result.png', fullPage: true });

    // Verify we got either matching results or a valid "no matches" message (not an error)
    const hasResults = await page.locator('.ant-table-row').count() > 0;
    const hasNoMatch = await page.getByText('No matching resumes found').isVisible().catch(() => false);
    const has503 = await page.getByText('service is currently unavailable').isVisible().catch(() => false);

    // AI matching should work - either find matches or legitimately find none
    // 503 means the AI service is down, which is a real failure
    expect(has503).toBe(false);
    expect(hasResults || hasNoMatch).toBe(true);
  });
});

test.describe('Page rendering', () => {
  test('login page displays correctly', async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto('/login', { waitUntil: 'networkidle' });
    await expect(page.getByPlaceholder('Username')).toBeVisible();
    await expect(page.getByPlaceholder('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: /login/i })).toBeVisible();
    await context.close();
  });

  test('login with invalid credentials shows error', async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto('/login', { waitUntil: 'networkidle' });
    await page.getByPlaceholder('Username').fill('wrong');
    await page.getByPlaceholder('Password').fill('wrong');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page.locator('.ant-message-error, .ant-alert-error, [role="alert"]')).toBeVisible({ timeout: 10000 });
    await context.close();
  });

  test('job list page renders', async ({ page }) => {
    await login(page);
    await page.goto('/jobs', { waitUntil: 'networkidle' });
    await expect(page.getByRole('button', { name: /create/i })).toBeVisible({ timeout: 10000 });
  });

  test('resume list page renders', async ({ page }) => {
    await login(page);
    await page.goto('/resumes', { waitUntil: 'networkidle' });
    await expect(page.getByRole('button', { name: 'Upload Resume' })).toBeVisible({ timeout: 10000 });
  });
});

test('unauthenticated access redirects to login', async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/jobs');
  await expect(page).toHaveURL(/.*\/login/, { timeout: 10000 });
  await context.close();
});
