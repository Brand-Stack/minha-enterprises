import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-cash-booking-report',
  templateUrl: './cash-booking-report.component.html',
  styleUrls: ['./cash-booking-report.component.scss']
})
export class CashBookingReportComponent implements OnInit {
  filterForm!: FormGroup;
  amountStatusSuggestions = ['Pending', 'Partial', 'Paid'];
  monthOptions = [
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' }
  ];

  rows: any[] = [];
  total = 0;
  page = 0;
  size = 20;
  loading = false;
  totals: { totalRecords: number; totalAmount: number } | null = null;

  customerSuggestions: string[] = [];
  filteredCustomerSuggestions: string[] = [];

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private router: Router,
    private fb: FormBuilder
  ) {}

  openEntry(r: { id?: string }): void {
    if (!r?.id) return;
    this.router.navigate(['/cash-booking/edit', r.id]);
  }

  ngOnInit(): void {
    this.filterForm = this.fb.group({
      customerName: [''],
      month: [''],
      year: [''],
      invoiceDateFrom: [null],
      invoiceDateTo: [null],
      amountStatus: [''],
      invoiceNumber: [''],
      description: ['']
    });

    this.loadCustomerSuggestions();
    this.load(0);
  }

  private toIsoDate(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private params(): Record<string, string | number> {
    const vals = this.filterForm.value;
    const p: Record<string, string | number> = { page: this.page, size: this.size, sortBy: 'bookingDate', sortDir: 'desc' };

    const cn = (vals.customerName || '').trim();
    if (cn) p['receiverName'] = cn;

    const awb = (vals.invoiceNumber || '').trim();
    if (awb) p['awbNo'] = awb;

    let df = this.toIsoDate(vals.invoiceDateFrom);
    let dt = this.toIsoDate(vals.invoiceDateTo);
    const m = Number(vals.month);
    const y = Number(vals.year);
    if (Number.isInteger(m) && m >= 1 && m <= 12) {
      const yearVal = (Number.isInteger(y) && y > 0) ? y : new Date().getFullYear();
      const from = new Date(yearVal, m - 1, 1);
      const to = new Date(yearVal, m, 0);
      df = this.toIsoDate(from);
      dt = this.toIsoDate(to);
    } else if (Number.isInteger(y) && y > 0) {
      df = `${y}-01-01`;
      dt = `${y}-12-31`;
    }

    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;

    const amt = (vals.amountStatus || '').trim();
    if (amt) p['amountStatus'] = amt;

    const desc = (vals.description || '').trim();
    if (desc) p['remarks'] = desc;

    return p;
  }

  load(pageIdx: number): void {
    this.page = pageIdx;
    this.loading = true;
    this.api.get<PageResponse<any>>('/cash-bookings/search', this.params()).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.total = res?.totalElements ?? 0;
        this.loading = false;
        this.loadTotals();
      },
      error: () => {
        this.loading = false;
        this.toast.error('Error', 'Failed to load');
      }
    });
  }

  loadTotals(): void {
    this.api.get<{ totalRecords: number; totalAmount: number }>('/cash-bookings/report-totals', this.params()).subscribe({
      next: (t) => (this.totals = t),
      error: () => (this.totals = null)
    });
  }

  reset(): void {
    this.filterForm.reset();
    this.filteredCustomerSuggestions = [...this.customerSuggestions];
    this.load(0);
  }

  onCustomerSearchChange(): void {
    const value = this.filterForm.get('customerName')?.value || '';
    const q = value.trim().toLowerCase();
    this.filteredCustomerSuggestions = !q
      ? [...this.customerSuggestions]
      : this.customerSuggestions.filter((name) => name.toLowerCase().includes(q));
  }

  private loadCustomerSuggestions(): void {
    const names = new Set<string>();
    this.api.get<any[]>('/clients/type/CUSTOMER').subscribe({
      next: (list) => {
        (list || []).forEach((c: any) => {
          if (c?.partyName) names.add(c.partyName);
        });
        this.customerSuggestions = Array.from(names).sort();
        this.filteredCustomerSuggestions = [...this.customerSuggestions];
      },
      error: () => {}
    });
    this.api.get<PageResponse<any>>('/collection-customers', { page: 0, size: 1000, sortBy: 'customerName', sortDir: 'asc' }).subscribe({
      next: (res) => {
        (res?.content || []).forEach((c: any) => {
          if (c?.customerName) names.add(c.customerName);
        });
        this.customerSuggestions = Array.from(names).sort();
        this.filteredCustomerSuggestions = [...this.customerSuggestions];
      },
      error: () => {}
    });
  }

  onPage(e: any): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  exportCsv(): void {
    const p = { ...this.params(), page: 0, size: 5000 };
    this.api.get<PageResponse<any>>('/cash-bookings/search', p).subscribe({
      next: (res) => {
        const list = res?.content ?? [];
        const esc = (v: string) => '"' + String(v ?? '').replace(/"/g, '""') + '"';
        const lines = [
          ['Date', 'Receiver', 'Pincode', 'State', 'City', 'Area', 'AWB', 'Courier', 'Amount', 'Amt status', 'Status', 'Remarks'].join(','),
          ...list.map((x: any) =>
            [
              x.bookingDate,
              esc(x.receiverName ?? ''),
              x.pincode ?? '',
              esc(x.state ?? ''),
              esc(x.city ?? ''),
              esc(x.areaName ?? ''),
              x.awbNo ?? '',
              esc(x.courier ?? ''),
              x.amount ?? '',
              esc(x.amountStatus ?? ''),
              esc(x.status ?? ''),
              esc(x.remarks ?? '')
            ].join(',')
          )
        ];

        let party = 'CashBooking';
        const cn = this.filterForm.get('customerName')?.value || '';
        if (cn.trim()) {
          party = cn.trim();
        }

        let monthStr = 'ALL';
        let yearStr = 'ALL';
        const m = Number(this.filterForm.get('month')?.value);
        const y = Number(this.filterForm.get('year')?.value);

        if (Number.isInteger(m) && m >= 1 && m <= 12) {
          const dateObj = new Date(2000, m - 1, 1);
          monthStr = dateObj.toLocaleString('en-US', { month: 'long' }).toUpperCase();
        }
        if (Number.isInteger(y) && y > 0) {
          yearStr = String(y);
        }

        const cleanParty = party.replace(/[^a-zA-Z0-9]/g, '');
        const filename = `${cleanParty}_${monthStr}_${yearStr}.csv`;

        const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        a.click();
        URL.revokeObjectURL(a.href);
        this.toast.success('Export', `${list.length} row(s)`);
      },
      error: () => this.toast.error('Error', 'Export failed')
    });
  }
}
