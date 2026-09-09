import { test, expect } from '@playwright/test';
import { UI_URL } from './site';

// Smoke: the served inbox placeholder renders the product identity + API hints.
// Phase-1 will extend this file with real inbox flows (login, assign, reply, CSAT).
test('inbox placeholder renders product identity', async ({ page }) => {
  await page.goto(UI_URL);
  await expect(page).toHaveTitle(/ETG Inbox/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Enterprise Texting Gateway');
  await expect(page.getByText('POST /v1/messages')).toBeVisible();
  await expect(page.getByText('GET /actuator/health')).toBeVisible();
});

test('placeholder tells agents what is coming next', async ({ page }) => {
  await page.goto(UI_URL);
  await expect(page.getByText(/Next\.js app lands here/i)).toBeVisible();
});
