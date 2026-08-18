import { expect, test } from '@playwright/test';

import { signIn, tokenOf } from './support/accounts';

/**
 * FR-027. Each case asserts twice: that the UI redirects, **and** that a direct API call
 * carrying the same token is rejected by the server.
 *
 * The second assertion is the one that matters. A suite that only checked the redirect
 * would pass just as happily against a server with no authorization at all, since
 * client-side guarding is UX and nothing more.
 */
test.describe('route protection', () => {
  test('a processor is redirected away from /review and refused by the API', async ({ page }) => {
    await signIn(page, 'processor');

    await page.goto('/review');
    await expect(page).toHaveURL(/\/403$/);
    await expect(page.getByRole('heading', { name: /do not have access/i })).toBeVisible();

    const response = await page.request.get('/api/v1/review/queue', {
      headers: { Authorization: `Bearer ${await tokenOf(page)}` },
    });
    expect(response.status()).toBe(403);
  });

  test('a processor is refused the audit endpoints', async ({ page }) => {
    await signIn(page, 'processor');

    await page.goto('/audit/journals');
    await expect(page).toHaveURL(/\/403$/);

    const token = await tokenOf(page);
    const journals = await page.request.get('/api/v1/audit/journals', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(journals.status()).toBe(403);
  });

  test('a reviewer cannot submit claims, by UI or by API', async ({ page }) => {
    await signIn(page, 'reviewer');

    await page.goto('/claims/new');
    await expect(page).toHaveURL(/\/403$/);

    const response = await page.request.post('/api/v1/claims', {
      headers: {
        Authorization: `Bearer ${await tokenOf(page)}`,
        'Idempotency-Key': crypto.randomUUID(),
      },
      data: {
        providerNpi: '1000000001',
        memberReference: 'MBR-SHOULDNOTPOST',
        serviceDate: '2026-01-15',
        billedAmount: '100.00',
        lines: [{ serviceCode: 'MP101', diagnosisCode: 'E1165', billedAmount: '100.00' }],
      },
    });
    expect(response.status()).toBe(403);
  });

  test('an auditor is read-only — the ledger is not writable from that role', async ({ page }) => {
    await signIn(page, 'auditor');

    await page.goto('/review');
    await expect(page).toHaveURL(/\/403$/);

    const token = await tokenOf(page);
    const approve = await page.request.post(
      `/api/v1/review/claims/${crypto.randomUUID()}/approve`,
      { headers: { Authorization: `Bearer ${token}` }, data: { note: 'should not be possible' } },
    );
    // 403 on the role check, which runs before the claim is ever looked up.
    expect(approve.status()).toBe(403);
  });

  test('an unauthenticated API call is 401, and the UI sends the visitor to /login', async ({
    page,
  }) => {
    await page.goto('/login');

    const response = await page.request.get('/api/v1/claims');
    expect(response.status()).toBe(401);

    await page.goto('/claims');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('an unknown path renders the 404 screen rather than a server error', async ({ page }) => {
    await signIn(page, 'processor');
    await page.goto('/no-such-screen');

    await expect(page.getByRole('heading', { name: /page not found/i })).toBeVisible();
  });
});
