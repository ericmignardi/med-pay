import { expect, type Page } from '@playwright/test';

/** The demo credentials seeded by `V3__seed_users_and_roles.sql`. */
export const ACCOUNTS = {
  processor: { email: 'processor@medpay.test', password: 'Demo!Pass123', landing: '/claims' },
  reviewer: { email: 'reviewer@medpay.test', password: 'Demo!Pass123', landing: '/review' },
  auditor: { email: 'auditor@medpay.test', password: 'Demo!Pass123', landing: '/audit/journals' },
} as const;

export type AccountName = keyof typeof ACCOUNTS;

/** Signs in through the real form, so the specs exercise the same path a user does. */
export async function signIn(page: Page, account: AccountName): Promise<void> {
  const { email, password, landing } = ACCOUNTS[account];

  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(new RegExp(`${landing}$`));
}

/** Reads the JWT the app stored, for specs that need to call the API directly. */
export async function tokenOf(page: Page): Promise<string> {
  const token = await page.evaluate(() => sessionStorage.getItem('medpay.token'));
  expect(token, 'expected a token in sessionStorage after sign-in').not.toBeNull();
  return token as string;
}

/** A unique member reference per run, so re-runs never collide on the claim fingerprint. */
export function uniqueMemberReference(): string {
  const suffix = Math.random().toString(36).slice(2, 10).toUpperCase().padEnd(8, 'X');
  return `MBR-E2E${suffix}`;
}
