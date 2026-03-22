import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display login page', async ({ page }) => {
    await expect(page.locator('text=登录')).toBeVisible();
  });

  test('should login successfully with valid credentials', async ({ page }) => {
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);
    await expect(page.locator('text=欢迎')).toBeVisible();
  });

  test('should show error with invalid credentials', async ({ page }) => {
    await page.fill('input[name="username"]', 'invalid');
    await page.fill('input[name="password"]', 'invalid');
    await page.click('button[type="submit"]');
    await expect(page.locator('text=用户名或密码错误')).toBeVisible();
  });

  test('should logout successfully', async ({ page }) => {
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);
    await page.click('[data-testid="user-menu"]');
    await page.click('text=退出登录');
    await expect(page).toHaveURL(/.*login.*/);
  });
});
