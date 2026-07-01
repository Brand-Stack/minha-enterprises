/** Parse filename from Content-Disposition (ASCII or RFC 5987 filename*). */
export function filenameFromContentDisposition(
  contentDisposition: string | null,
  fallback: string
): string {
  if (!contentDisposition) return fallback;
  const star = contentDisposition.match(/filename\*=(?:UTF-8'')?([^;]+)/i);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^["']|["']$/g, ''));
    } catch {
      /* ignore */
    }
  }
  const quoted = contentDisposition.match(/filename="((?:\\.|[^"\\])*)"/i);
  if (quoted?.[1]) {
    return quoted[1].replace(/\\(.)/g, '$1');
  }
  const simple = contentDisposition.match(/filename=([^;]+)/i);
  if (simple?.[1]) {
    return simple[1].replace(/^["']|["']$/g, '').trim();
  }
  return fallback;
}

const ILLEGAL = /[/\\:*?"<>|\u0000-\u001F]/g;

export function sanitizeDownloadFilenameSegment(s: string): string {
  return s.replace(ILLEGAL, '').replace(/\s+/g, ' ').trim();
}

export function monthlyInvoiceDownloadFilename(
  q: { customerName?: string | null; month?: string | null; year?: number | null },
  ext: string
): string {
  const customer = sanitizeDownloadFilenameSegment(q.customerName || 'Customer') || 'Customer';
  const month = (q.month || '').trim().replace(ILLEGAL, '');
  const y = q.year != null && !Number.isNaN(q.year) ? String(q.year) : '';
  let base = `${customer} - Monthly Invoice - ${month}${month && y ? ' ' : ''}${y}`;
  base = sanitizeDownloadFilenameSegment(base) || 'Monthly Invoice';
  return `${base}.${ext.replace(/^\./, '')}`;
}

export function monthlyBreakupPdfFallback(
  q: { customerName?: string | null; month?: string | null; year?: number | null }
): string {
  const customer = sanitizeDownloadFilenameSegment(q.customerName || 'Customer') || 'Customer';
  const month = (q.month || '').trim().replace(ILLEGAL, '');
  const y = q.year != null && !Number.isNaN(q.year) ? String(q.year) : '';
  let base = `${customer} - Monthly Invoice - ${month}${month && y ? ' ' : ''}${y}`;
  base = sanitizeDownloadFilenameSegment(base) || 'Monthly Invoice';
  return `${base} - Breakup.pdf`;
}

export function courierCustomerDownloadFilename(
  customerName: string | null | undefined,
  ext: string
): string {
  const c = sanitizeDownloadFilenameSegment(customerName || 'Customer') || 'Customer';
  return `${c}.${ext.replace(/^\./, '')}`;
}
