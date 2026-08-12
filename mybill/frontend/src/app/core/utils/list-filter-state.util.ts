/** Persist list filter + pagination state across edit/navigation flows. */
export function saveListFilterState(storageKey: string, state: Record<string, unknown>): void {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    /* ignore quota errors */
  }
}

export function loadListFilterState<T extends Record<string, unknown>>(storageKey: string): T | null {
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function clearListFilterState(storageKey: string): void {
  try {
    sessionStorage.removeItem(storageKey);
  } catch {
    /* ignore */
  }
}

export function parseIsoDate(s: unknown): Date | null {
  if (!s || typeof s !== 'string') return null;
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function isoDateString(d: Date | null): string | undefined {
  if (!d) return undefined;
  const x = new Date(d);
  return (
    x.getFullYear() +
    '-' +
    String(x.getMonth() + 1).padStart(2, '0') +
    '-' +
    String(x.getDate()).padStart(2, '0')
  );
}
