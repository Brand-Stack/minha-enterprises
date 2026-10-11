/** Month names aligned with Client Entry (`MonthlyCourierQuotationFormComponent`). */
export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December'
];

export function currentMonthName(): string {
  return MONTH_NAMES[new Date().getMonth()];
}

export function buildYearOptions(past: number, future: number): number[] {
  const y = new Date().getFullYear();
  const out: number[] = [];
  for (let i = y - past; i <= y + future; i++) {
    out.push(i);
  }
  return out;
}
