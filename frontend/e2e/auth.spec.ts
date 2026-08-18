import { expect, test } from '@playwright/test';

import { ACCOUNTS, signIn } from './support/accounts';

test.describe('authentication', () => {
  test('a processor signs in and lands on their claims', async ({ page }) => {
    await signIn(page, 'processor');

    await expect(page.getByRole('heading', { name: 'Your claims' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  });

  test('each role lands on the screen its role can actually use', async ({ page }) => {
    await signIn(page, 'reviewer');
    await expect(page.getByRole('heading', { name: 'Review queue' })).toBeVisible();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await signIn(page, 'auditor');
    await expect(page.getByRole('heading', { name: 'Ledger audit' })).toBeVisible();
  });

  test('a wrong password is rejected inline without leaving the form', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ACCOUNTS.processor.email);
    await page.getByLabel('Password').fill('not-the-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('an unknown email fails identically to a wrong password', async ({ page }) => {
    // NFR-006: the responses must not let an attacker enumerate valid accounts.
    await page.goto('/login');
    await page.getByLabel('Email').fill('nobody@medpay.test');
    await page.getByLabel('Password').fill('Demo!Pass123');
    await page.getByRole('button', { name: 'Sign in' }).click();

    const unknownEmailMessage = await page.getByRole('alert').textContent();

    await page.getByLabel('Email').fill(ACCOUNTS.processor.email);
    await page.getByLabel('Password').fill('not-the-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByRole('alert')).toHaveText(unknownEmailMessage ?? '');
  });

  test('a deep link bounces through login and returns to the original target', async ({ page }) => {
    await page.goto('/audit/journals');
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel('Email').fill(ACCOUNTS.auditor.email);
    await page.getByLabel('Password').fill(ACCOUNTS.auditor.password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    // FR-027: the attempted path is preserved in router state, not discarded.
    await expect(page).toHaveURL(/\/audit\/journals$/);
    await expect(page.getByRole('heading', { name: 'Ledger audit' })).toBeVisible();
  });
});
