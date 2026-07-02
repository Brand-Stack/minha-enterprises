import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiService } from './api.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import {
  filenameFromContentDisposition,
  monthlyBreakupPdfFallback,
  monthlyInvoiceDownloadFilename
} from '../utils/content-disposition-filename.util';

@Injectable({ providedIn: 'root' })
export class InvoicePreviewService {
  constructor(
    private http: HttpClient,
    private api: ApiService,
    private toast: ToastService
  ) {}

  viewInvoice(quotationId: string, row?: { customerName?: string; month?: string; year?: number }, isSmallClient = false): void {
    this.openInvoice(quotationId, 'VIEW', row, isSmallClient);
  }

  printInvoice(quotationId: string, row?: { customerName?: string; month?: string; year?: number }, isSmallClient = false): void {
    this.openInvoice(quotationId, 'PRINT', row, isSmallClient);
  }

  downloadInvoice(quotationId: string, includeBreakup: boolean, row?: { customerName?: string; month?: string; year?: number }, isSmallClient = false): void {
    const base = this.api.getBaseUrl();
    const route = isSmallClient ? 'small-client-entries' : 'monthly-courier-quotations';
    const endpoint =
      `${base}/${route}/${quotationId}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=DOWNLOAD` +
      (includeBreakup ? '' : '&includeBreakup=false');
    this.http.get(endpoint, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res) => {
        const fallback = monthlyInvoiceDownloadFilename(
          { customerName: row?.customerName, month: row?.month, year: row?.year },
          'pdf'
        );
        const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
        this.downloadBlob(res.body!, name);
        this.toast.success('Success', 'Invoice PDF downloaded');
      },
      error: () => this.toast.error('Error', 'Failed to download invoice PDF')
    });
  }

  viewBreakup(quotationId: string, isSmallClient = false): void {
    const route = isSmallClient ? 'small-client-entries' : 'monthly-courier-quotations';
    const url = `${this.api.getBaseUrl()}/${route}/${quotationId}/breakup-pdf?includeAmount=true&includeWeight=true`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const obj = window.URL.createObjectURL(blob);
        window.open(obj, '_blank', 'noopener');
        setTimeout(() => window.URL.revokeObjectURL(obj), 60000);
        this.toast.success('View', 'Breakup preview opened');
      },
      error: () => this.toast.error('Error', 'Failed to open breakup preview')
    });
  }

  printBreakup(quotationId: string, isSmallClient = false): void {
    const route = isSmallClient ? 'small-client-entries' : 'monthly-courier-quotations';
    const url = `${this.api.getBaseUrl()}/${route}/${quotationId}/breakup-pdf?includeAmount=true&includeWeight=true`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const obj = window.URL.createObjectURL(blob);
        const frame = document.createElement('iframe');
        frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
        frame.src = obj;
        frame.onload = () => {
          frame.contentWindow?.focus();
          frame.contentWindow?.print();
        };
        document.body.appendChild(frame);
        setTimeout(() => {
          document.body.removeChild(frame);
          window.URL.revokeObjectURL(obj);
        }, 60000);
        this.toast.success('Print', 'Breakup print dialog opened');
      },
      error: () => this.toast.error('Error', 'Failed to print breakup')
    });
  }

  downloadBreakup(quotationId: string, row?: { customerName?: string; month?: string; year?: number }, isSmallClient = false): void {
    const route = isSmallClient ? 'small-client-entries' : 'monthly-courier-quotations';
    const url = `${this.api.getBaseUrl()}/${route}/${quotationId}/breakup-pdf`;
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res) => {
        const fallback = monthlyBreakupPdfFallback({
          customerName: row?.customerName,
          month: row?.month,
          year: row?.year
        });
        const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
        this.downloadBlob(res.body!, name);
        this.toast.success('Success', 'Breakup PDF downloaded');
      },
      error: () => this.toast.error('Error', 'Failed to download breakup PDF')
    });
  }

  private openInvoice(
    quotationId: string,
    action: 'VIEW' | 'PRINT',
    row?: { customerName?: string; month?: string; year?: number },
    isSmallClient = false
  ): void {
    const route = isSmallClient ? 'small-client-entries' : 'monthly-courier-quotations';
    const endpoint = `${this.api.getBaseUrl()}/${route}/${quotationId}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=${action}`;
    this.http.get(endpoint, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        if (action === 'PRINT') {
          const frame = document.createElement('iframe');
          frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
          frame.src = url;
          frame.onload = () => {
            frame.contentWindow?.focus();
            frame.contentWindow?.print();
          };
          document.body.appendChild(frame);
          setTimeout(() => {
            document.body.removeChild(frame);
            window.URL.revokeObjectURL(url);
          }, 60000);
          this.toast.success('Print', 'Invoice print dialog opened');
        } else {
          window.open(url, '_blank', 'noopener');
          setTimeout(() => window.URL.revokeObjectURL(url), 60000);
          this.toast.success('View', 'Invoice preview opened');
        }
      },
      error: () => this.toast.error('Error', action === 'PRINT' ? 'Failed to print invoice' : 'Failed to view invoice')
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
