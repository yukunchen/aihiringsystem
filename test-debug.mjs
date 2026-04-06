import { chromium } from 'playwright';

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  // Collect console errors
  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });
  page.on('pageerror', err => {
    consoleErrors.push(`Page error: ${err.message}`);
  });

  // Listen for network responses to track the API calls
  const apiResponses = [];
  page.on('response', resp => {
    if (resp.url().includes('/api/')) {
      apiResponses.push({ url: resp.url(), status: resp.status() });
    }
  });

  // Step 1: Login
  console.log('Step 1: Navigating to login page...');
  await page.goto('http://localhost:3011/login', { waitUntil: 'networkidle' });
  await page.screenshot({ path: '/tmp/step1-login.png' });

  // Fill in login form
  await page.fill('input[id="username"]', 'admin');
  await page.fill('input[id="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/jobs**', { timeout: 10000 }).catch(() => console.log('Login redirect timeout'));
  console.log('Step 2: After login, URL:', page.url());
  await page.screenshot({ path: '/tmp/step2-after-login.png' });

  // Step 2: Wait for job list to load
  await page.waitForSelector('table', { timeout: 10000 }).catch(() => console.log('Table not found'));
  console.log('Step 3: Job list loaded');
  await page.screenshot({ path: '/tmp/step3-joblist.png' });

  // Step 3: Click "View" button
  const viewButton = await page.locator('button:has-text("View")').first();
  if (await viewButton.isVisible()) {
    console.log('Step 4: Clicking View button...');
    await viewButton.click();

    // Wait for either the detail page content or a timeout
    await page.waitForTimeout(5000);
    console.log('Step 5: After clicking View, URL:', page.url());
    await page.screenshot({ path: '/tmp/step5-detail-page.png' });

    // Check if still spinning
    const spinExists = await page.locator('.ant-spin').count();
    console.log('Spin element count:', spinExists);

    // Check page content
    const bodyText = await page.locator('body').textContent();
    console.log('Page text (first 500 chars):', bodyText.substring(0, 500));

    // Check for specific content
    const hasJobTitle = await page.locator('text=Unity').count();
    const hasDescription = await page.locator('text=XR').count();
    const hasDepartment = await page.locator('text=Headquarters').count();
    const hasDRAFT = await page.locator('text=DRAFT').count();

    console.log('Has job title:', hasJobTitle);
    console.log('Has description:', hasDescription);
    console.log('Has department:', hasDepartment);
    console.log('Has DRAFT status:', hasDRAFT);

  } else {
    console.log('View button not found!');
  }

  console.log('\nConsole errors:', consoleErrors);
  console.log('\nAPI responses:', apiResponses);

  await browser.close();
})();
