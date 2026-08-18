import Decimal from 'decimal.js';

/**
 * Money helpers. Every input is a decimal string from the API (PRD §5) and every
 * intermediate is a `Decimal` — no value here is ever a JavaScript `number`.
 *
 * Grouping is done by string manipulation rather than `Intl.NumberFormat`. `Intl`'s
 * `format()` takes a `number` in the TypeScript DOM lib, and money columns are
 * `NUMERIC(19,4)`: an integer part of up to fifteen digits exceeds `Number.MAX_SAFE_INTEGER`
 * (~9.0e15), so routing through `Intl` would silently round the very amounts that matter
 * most. The output shape is identical to `en-CA` currency formatting.
 */

Decimal.set({ precision: 34, rounding: Decimal.ROUND_HALF_UP });

export const ZERO = new Decimal(0);

/** Parses a decimal string. Returns null rather than throwing on malformed input. */
export function parseMoney(value: string | null | undefined): Decimal | null {
  if (value === null || value === undefined || value.trim() === '') {
    return null;
  }
  try {
    const parsed = new Decimal(value);
    return parsed.isFinite() ? parsed : null;
  } catch {
    return null;
  }
}

/** Sums decimal strings exactly. Unparseable entries contribute nothing. */
export function sumMoney(values: readonly string[]): Decimal {
  return values.reduce<Decimal>((total, value) => {
    const parsed = parseMoney(value);
    return parsed === null ? total : total.plus(parsed);
  }, ZERO);
}

/** Renders as `$1,234.50`. Null and unparseable render as an em dash. */
export function formatMoney(value: string | Decimal | null | undefined): string {
  const decimal = value instanceof Decimal ? value : parseMoney(value ?? null);
  if (decimal === null) {
    return '—';
  }

  const fixed = decimal.toDecimalPlaces(2, Decimal.ROUND_HALF_UP).toFixed(2);
  const negative = fixed.startsWith('-');
  const unsigned = negative ? fixed.slice(1) : fixed;
  const [whole = '0', fraction = '00'] = unsigned.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  return `${negative ? '-' : ''}$${grouped}.${fraction}`;
}

/** The canonical two-decimal wire form, for values sent back to the API. */
export function toWireAmount(value: string | Decimal): string {
  const decimal = value instanceof Decimal ? value : parseMoney(value);
  return (decimal ?? ZERO).toDecimalPlaces(2, Decimal.ROUND_HALF_UP).toFixed(2);
}

export function isSameToTheCent(left: Decimal, right: Decimal): boolean {
  return left
    .toDecimalPlaces(2, Decimal.ROUND_HALF_UP)
    .equals(right.toDecimalPlaces(2, Decimal.ROUND_HALF_UP));
}

export { Decimal };
