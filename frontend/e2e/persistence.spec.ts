import { expect, test } from '@playwright/test';

import { signIn } from './support/accounts';

test.describe('session persistence', () => {
  test('the session survives a hard reload', async ({ page }) => {
    await signIn(page, 'processor');

    await page.reload();

    // The provider rehydrates from GET /auth/me rather than trusting the cached
    // profile, so this also proves the token is still accepted server-side.
    await expect(page.getByRole('heading', { name: 'Your claims' })).toBeVisible();
    await expect(page).toHaveURL(/\/claims$/);
  });

  test('navigating between routes keeps the identity without re-authenticating', async ({
    page,
  }) => {
    await signIn(page, 'processor');

    await page.getByRole('link', { name: 'New claim' }).first().click();
    await expect(page).toHaveURL(/\/claims\/new$/);
    await expect(page.getByRole('heading', { name: 'Submit a claim' })).toBeVisible();

    await page.goBack();
    await expect(page.getByRole('heading', { name: 'Your claims' })).toBeVisible();
  });

  test('the session does not outlive the browsing context', async ({ browser }) => {
    // sessionStorage rather than localStorage (NFR-002): a fresh context is a fresh
    // tab, and must not inherit the credential.
    const first = await browser.newContext();
    const page = await first.newPage();
    await signIn(page, 'processor');
    await first.close();

    const second = await browser.newContext();
    const freshPage = await second.newPage();
    await freshPage.goto('/claims');
    await expect(freshPage).toHaveURL(/\/login$/);
    await second.close();
  });

  test('signing out clears the credential, not just the screen', async ({ page }) => {
    await signIn(page, 'processor');
    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);

    const token = await page.evaluate(() => sessionStorage.getItem('medpay.token'));
    expect(token).toBeNull();

    await page.goto('/claims');
    await expect(page).toHaveURL(/\/login$/);
  });
});
