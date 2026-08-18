import { expect, test } from '@playwright/test';

import { signIn } from './support/accounts';

test.describe('token expiry', () => {
  test('an expired token routes to /login?expired=1 through the interceptor', async ({ page }) => {
    await signIn(page, 'processor');

    // A structurally valid but unacceptable token: the server must answer 401, and the
    // response interceptor must turn that into a sign-out plus the expired banner.
    await page.evaluate(() => {
      sessionStorage.setItem(
        'medpay.token',
        'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJleHBpcmVkIiwiZXhwIjoxfQ.c2lnbmF0dXJlLWlzLW5vdC12YWxpZA',
      );
    });

    await page.goto('/claims');

    await expect(page).toHaveURL(/\/login\?expired=1$/);
    await expect(page.getByText(/session expired/i)).toBeVisible();

    // The dead credential is cleared rather than left to fail on every later call.
    expect(await page.evaluate(() => sessionStorage.getItem('medpay.token'))).toBeNull();
  });

  test('a 401 never renders as a 500 or a blank screen', async ({ page }) => {
    await page.goto('/login');
    const response = await page.request.get('/api/v1/claims', {
      headers: { Authorization: 'Bearer not-a-real-token' },
    });

    expect(response.status()).toBe(401);
    const body: unknown = await response.json();
    expect(body).toMatchObject({ status: 401 });
  });

  test('signing in again after expiry restores a working session', async ({ page }) => {
    await signIn(page, 'processor');
    await page.evaluate(() => {
      sessionStorage.setItem('medpay.token', 'clearly.invalid.token');
    });
    await page.goto('/claims');
    await expect(page).toHaveURL(/expired=1$/);

    await signIn(page, 'processor');
    await expect(page.getByRole('heading', { name: 'Your claims' })).toBeVisible();
  });
});
