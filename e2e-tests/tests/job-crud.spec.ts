import { test, expect } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

const JOB_TITLE_PREFIX = 'E2E Unity中高级开发工程师 ';
const JOB_DESCRIPTION = [
  '岗位职责：',
  '1. 使用Unity3D引擎进行游戏/应用开发',
  '2. 负责核心功能模块的设计与实现',
  '3. 优化渲染性能和内存使用',
  '4. 与策划、美术团队协作推进项目',
  '',
  '任职要求：',
  '1. 3年以上Unity开发经验',
  '2. 熟悉C#语言和面向对象设计',
  '3. 了解Shader编程和图形学基础',
  '4. 有完整项目上线经验者优先',
].join('\n');

let createdJobTitle: string;

test('Test 6: create job successfully', async ({ page }) => {
  createdJobTitle = JOB_TITLE_PREFIX + Date.now();

  // Wait for departments API
  const deptResponse = page.waitForResponse(
    r => r.url().includes('/api/departments') && r.status() === 200,
  );
  await page.goto('/jobs/new');
  await deptResponse;

  // Fill title
  await page.getByPlaceholder('Title').fill(createdJobTitle);

  // Select first department
  await page.getByPlaceholder('Select department').click();
  const dropdown = page.locator('.ant-select-dropdown:visible');
  await expect(dropdown).toBeVisible({ timeout: 5000 });
  await dropdown.locator('.ant-select-item-option').first().click();

  // Fill description
  await page.getByPlaceholder('Description').fill(JOB_DESCRIPTION);

  // Submit
  await page.getByRole('button', { name: /submit/i }).click();

  // Should redirect to job detail page
  await page.waitForURL(/\/jobs\/[a-f0-9-]+/, { timeout: 15000 });

  // Verify detail page shows the title
  await expect(page.getByText(createdJobTitle)).toBeVisible({ timeout: 10000 });
});

test('Test 7: data persists after reload', async ({ page }) => {
  // Go to jobs list
  await page.goto('/jobs');
  await expect(page.locator('table')).toBeVisible({ timeout: 10000 });

  // Verify created job is visible
  await expect(page.getByText(createdJobTitle)).toBeVisible({ timeout: 10000 });

  // Reload
  await page.reload();
  await expect(page.locator('table')).toBeVisible({ timeout: 10000 });

  // Job should still be present after reload
  await expect(page.getByText(createdJobTitle)).toBeVisible({ timeout: 10000 });
});
