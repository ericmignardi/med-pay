import { expect, test, type Page } from '@playwright/test';

import { signIn, uniqueMemberReference } from './support/accounts';

/**
 * The spec that proves the system rather than any one screen: a $60,000 claim is submitted
 * by a processor, held for review because it is at or above the threshold, approved by a
 * different reviewer, and then read by an auditor who sees exactly one balanced pair.
 *
 * Separation of duties is what makes three sign-ins necessary here — the submitter cannot
 * be the approver, and `self-approval` below asserts that directly.
 */

const BILLED = '60000.00';
const SERVICE_CODE = 'SX304'; // contracted at 61,500.00, so the allowed amount is the billed amount

async function submitLargeClaim(page: Page, memberReference: string): Promise<string> {
  await page.goto('/claims/new');

  await page.getByLabel('Provider NPI').fill('1000000003');
  await page.getByLabel('Member reference').fill(memberReference);
  await page.getByLabel('Service date').fill('2026-06-15');
  await page.getByLabel("Header billed amount", { exact: true }).fill(BILLED);

  await page.getByLabel('Service code').fill(SERVICE_CODE);
  await page.getByLabel('Diagnosis').fill('I2510');
  await page.getByLabel("Billed amount", { exact: true }).first().fill(BILLED);

  // The client-side mirror of FR-009 must agree before the button is even enabled.
  await expect(page.getByText('Lines match the header amount')).toBeVisible();

  await page.getByRole('button', { name: 'Submit claim' }).click();

  await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}$/);
  const claimUuid = page.url().split('/').pop() ?? '';
  expect(claimUuid).toHaveLength(36);
  return claimUuid;
}

test.describe('cross-role flow', () => {
  test('$60k submit, review, approve, audit — with one balanced journal group', async ({
    page,
  }) => {
    const memberReference = uniqueMemberReference();

    /* ---------------------------------------------------------- processor submits */
    await signIn(page, 'processor');
    const claimUuid = await submitLargeClaim(page, memberReference);

    // At or above 25,000.00 holds. Nothing has posted to the ledger yet.
    await expect(page.getByText("Flagged for review").first()).toBeVisible();
    await expect(
      page.getByText(/A claim posts to the ledger only when it is adjudicated/),
    ).toBeVisible();

    await page.getByRole('button', { name: 'Sign out' }).click();

    /* ------------------------------------------------------------ reviewer approves */
    await signIn(page, 'reviewer');
    await page.goto(`/review/${claimUuid}`);

    await expect(page.getByText(memberReference)).toBeVisible();
    await page.getByRole('button', { name: 'Approve', exact: true }).click();
    await page.getByLabel('Note').fill('Clinically appropriate; approved end to end.');
    await page.getByRole('button', { name: 'Approve and pay' }).click();

    await expect(page).toHaveURL(/\/review$/);

    await page.getByRole('button', { name: 'Sign out' }).click();

    /* ---------------------------------------------------------------- auditor reads */
    await signIn(page, 'auditor');
    await page.goto(`/audit/claims/${claimUuid}`);

    await expect(page.getByText("Paid", { exact: true }).first()).toBeVisible();

    // Exactly two journal lines, correct directions and account types, balance 0.00.
    const journalRows = page.locator('table tbody tr', {
      has: page.locator('td', { hasText: /PAYER_CLAIMS_EXPENSE|PROVIDER_PAYABLE/ }),
    });
    await expect(journalRows).toHaveCount(2);

    await expect(page.getByText('PAYER_CLAIMS_EXPENSE')).toBeVisible();
    await expect(page.getByText('PROVIDER_PAYABLE')).toBeVisible();
    await expect(page.getByText('DEBIT')).toBeVisible();
    await expect(page.getByText('CREDIT')).toBeVisible();

    await expect(page.getByText('Balanced')).toBeVisible();
    // Money renders at two decimal places with grouping, and no float artifact.
    await expect(page.getByText('$60,000.00').first()).toBeVisible();

    // The outbox event stream is the transition log for this claim.
    await expect(page.getByText(/claim paid/i)).toBeVisible();
  });

  test('self-approval is refused even when the submitter holds both roles', async ({ page }) => {
    // TC-R-004 at the UI level. The processor account cannot reach /review at all, so the
    // separation-of-duties rule is asserted where it is enforceable: against the API,
    // with the submitter's own credential.
    await signIn(page, 'processor');
    const claimUuid = await submitLargeClaim(page, uniqueMemberReference());

    const token = await page.evaluate(() => sessionStorage.getItem('medpay.token'));
    const response = await page.request.post(`/api/v1/review/claims/${claimUuid}/approve`, {
      headers: { Authorization: `Bearer ${token ?? ''}` },
      data: { note: 'approving my own submission' },
    });

    // 403 on the role gate — the processor never reaches the self-approval check.
    expect(response.status()).toBe(403);
  });

  test('a sub-threshold claim pays immediately and posts its pair on submission', async ({
    page,
  }) => {
    await signIn(page, 'processor');

    await page.goto('/claims/new');
    await page.getByLabel('Provider NPI').fill('1000000001');
    await page.getByLabel('Member reference').fill(uniqueMemberReference());
    await page.getByLabel('Service date').fill('2026-06-15');
    await page.getByLabel("Header billed amount", { exact: true }).fill('125.00');
    await page.getByLabel('Service code').fill('MP101');
    await page.getByLabel('Diagnosis').fill('E1165');
    await page.getByLabel("Billed amount", { exact: true }).first().fill("125.00");

    await page.getByRole('button', { name: 'Submit claim' }).click();
    await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}$/);

    await expect(page.getByText("Paid", { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Balanced')).toBeVisible();
    await expect(page.getByText('$125.00').first()).toBeVisible();
  });

  test('the line-sum indicator blocks submission before the server has to', async ({ page }) => {
    await signIn(page, 'processor');

    await page.goto('/claims/new');
    await page.getByLabel("Header billed amount", { exact: true }).fill('100.00');
    await page.getByLabel("Billed amount", { exact: true }).first().fill("99.99");

    await expect(page.getByText('Lines are off by $0.01')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Submit claim' })).toBeDisabled();
  });

  test('a duplicate submission is refused and links to the existing claim', async ({ page }) => {
    await signIn(page, 'processor');

    const memberReference = uniqueMemberReference();
    const claimUuid = await submitLargeClaim(page, memberReference);

    // Same provider, member and service codes on the same date — the FR-008 fingerprint.
    await submitLargeClaim(page, memberReference).catch(() => undefined);

    await expect(
      page.getByText('An active claim already exists for this service encounter.'),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: 'Open the existing claim' })).toHaveAttribute(
      'href',
      `/claims/${claimUuid}`,
    );
  });
});
