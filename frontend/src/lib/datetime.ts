/**
 * Display helpers for the two temporal shapes the API uses: `Instant` (ISO-8601 with a
 * zone) and `LocalDate` (a calendar fact with neither instant nor zone, PRD §4.0).
 *
 * A `serviceDate` is deliberately never passed through `new Date()`. `new Date('2026-08-01')`
 * parses as midnight UTC and then renders in local time, which shows the previous day
 * anywhere west of Greenwich — a date of service must not drift.
 */

const INSTANT_FORMAT = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const MONTHS: Record<string, string> = {
  '01': 'Jan', '02': 'Feb', '03': 'Mar', '04': 'Apr', '05': 'May', '06': 'Jun',
  '07': 'Jul', '08': 'Aug', '09': 'Sep', '10': 'Oct', '11': 'Nov', '12': 'Dec',
};

/** Renders an ISO instant in the viewer's zone. */
export function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : INSTANT_FORMAT.format(parsed);
}

/** Renders an ISO_LOCAL_DATE by splitting the string — never by constructing a Date. */
export function formatServiceDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const [year, month, day] = value.split('-');
  if (year === undefined || month === undefined || day === undefined) {
    return value;
  }
  const monthName = MONTHS[month];
  return monthName === undefined ? value : `${monthName} ${day}, ${year}`;
}

/** Today as ISO_LOCAL_DATE in the viewer's zone, for date-input maxima. */
export function todayIsoDate(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
