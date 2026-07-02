/**
 * Calendar dates without UTC shift (fixes invoice/off-by-one when using ISO strings or toISOString()).
 */

export function parseIsoDateToLocal(iso: string | null | undefined): Date | null {
  if (iso == null || iso === '') return null;
  const part = String(iso).split('T')[0];
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(part);
  if (!m) return null;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  if (!y || !mo || !d) return null;
  return new Date(y, mo - 1, d);
}

/** Sends YYYY-MM-DD using local calendar fields (safe for LocalDate on server). */
export function formatLocalDateOnly(value: Date | string | null | undefined): string | undefined {
  if (value == null || value === '') return undefined;
  if (typeof value === 'string') {
    const p = value.split('T')[0];
    if (/^\d{4}-\d{2}-\d{2}$/.test(p)) return p;
    const d = parseIsoDateToLocal(value);
    return d ? formatLocalDateOnly(d) : undefined;
  }
  const d = value as Date;
  if (isNaN(d.getTime())) return undefined;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
