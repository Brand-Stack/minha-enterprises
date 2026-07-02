const MONTH_NAMES = [
  'JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE',
  'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'
];

export const CLIENT_ENTRY_LOCK_MESSAGE =
  'Editing is allowed only for the current and previous month. Please contact an administrator.';

function monthIndex(month: string | null | undefined): number {
  if (!month) return 0;
  const u = month.trim().toUpperCase();
  const idx = MONTH_NAMES.indexOf(u);
  return idx >= 0 ? idx + 1 : 0;
}

function toYearMonth(month: string | null | undefined, year: number | null | undefined): { year: number; month: number } | null {
  if (!month || year == null) return null;
  const m = monthIndex(month);
  if (m < 1) return null;
  return { year, month: m };
}

/** True when a previous-month Client Entry record is locked for non-admin users. */
export function isClientEntryRecordLocked(month: string | null | undefined, year: number | null | undefined, today = new Date()): boolean {
  const record = toYearMonth(month, year);
  if (!record) return false;
  const currentYear = today.getFullYear();
  const currentMonth = today.getMonth() + 1;
  const recordKey = record.year * 12 + record.month;
  const currentKey = currentYear * 12 + currentMonth;
  return recordKey < currentKey - 1;
}

export function canEditClientEntryRecord(
  month: string | null | undefined,
  year: number | null | undefined,
  isAdmin: boolean,
  today = new Date()
): boolean {
  return isAdmin || !isClientEntryRecordLocked(month, year, today);
}
