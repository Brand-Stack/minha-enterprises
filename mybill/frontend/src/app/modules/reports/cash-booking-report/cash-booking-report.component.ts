import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { Router } from '@angular/router';
import { gsap } from 'gsap';

@Component({
  selector: 'app-cash-booking-report',
  templateUrl: './cash-booking-report.component.html',
  styleUrls: ['./cash-booking-report.component.scss']
})
export class CashBookingReportComponent implements OnInit, AfterViewInit {
  @ViewChild('wrap', { static: false }) wrap?: ElementRef<HTMLElement>;

  rows: any[] = [];
  total = 0;
  page = 0;
  size = 20;
  loading = false;
  totals: { totalRecords: number; totalAmount: number } | null = null;
  monthOptions = this.buildMonthOptions();
  selectedMonth = '';
  customerSearch = '';
  customerSuggestions: string[] = [];
  filteredCustomerSuggestions: string[] = [];

  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  awbNo = '';
  receiverName = '';
  pincode = '';
  state = '';
  areaName = '';
  courier = '';
  status = '';
  amountStatus = '';
  remarks = '';

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private router: Router
  ) {}

  openEntry(r: { id?: string }): void {
    if (!r?.id) return;
    this.router.navigate(['/cash-booking/edit', r.id]);
  }

  ngOnInit(): void {
    this.loadCustomerSuggestions();
    this.load(0);
  }

  ngAfterViewInit(): void {
    if (!this.wrap?.nativeElement || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    gsap.from(this.wrap.nativeElement.querySelectorAll('.rpt-animate'), {
      opacity: 0,
      y: 12,
      duration: 0.35,
      stagger: 0.05,
      ease: 'power2.out'
    });
  }

  private iso(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private buildMonthOptions(): { value: string; label: string }[] {
    const options: { value: string; label: string }[] = [];
    const base = new Date();
    for (let i = 0; i < 18; i++) {
      const d = new Date(base.getFullYear(), base.getMonth() - i, 1);
      const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      options.push({ value, label: d.toLocaleString('en-IN', { month: 'long', year: 'numeric' }) });
    }
    return options;
  }

  private monthRange(): { from?: string; to?: string } {
    if (!this.selectedMonth) return {};
    const [year, month] = this.selectedMonth.split('-').map(Number);
    const from = new Date(year, month - 1, 1);
    const to = new Date(year, month, 0);
    return { from: this.iso(from), to: this.iso(to) };
  }

  private params(): Record<string, string | number> {
    const p: Record<string, string | number> = { page: this.page, size: this.size, sortBy: 'bookingDate', sortDir: 'desc' };
    const month = this.monthRange();
    const df = month.from || this.iso(this.dateFrom);
    const dt = month.to || this.iso(this.dateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;
    if (this.awbNo.trim()) p['awbNo'] = this.awbNo.trim();
    if (this.receiverName.trim()) p['receiverName'] = this.receiverName.trim();
    else if (this.customerSearch.trim()) p['receiverName'] = this.customerSearch.trim();
    if (this.pincode.trim()) p['pincode'] = this.pincode.trim();
    if (this.state.trim()) p['state'] = this.state.trim();
    if (this.areaName.trim()) p['areaName'] = this.areaName.trim();
    if (this.courier.trim()) p['courier'] = this.courier.trim();
    if (this.status.trim()) p['status'] = this.status.trim();
    if (this.amountStatus.trim()) p['amountStatus'] = this.amountStatus.trim();
    if (this.remarks.trim()) p['remarks'] = this.remarks.trim();
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
    this.selectedMonth = '';
    this.customerSearch = '';
    this.filteredCustomerSuggestions = [...this.customerSuggestions];
    this.dateFrom = null;
    this.dateTo = null;
    this.awbNo = '';
    this.receiverName = '';
    this.pincode = '';
    this.state = '';
    this.areaName = '';
    this.courier = '';
    this.status = '';
    this.amountStatus = '';
    this.remarks = '';
    this.load(0);
  }

  onCustomerSearchChange(value: string): void {
    const q = (value || '').trim().toLowerCase();
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
        if (this.receiverName && this.receiverName.trim()) {
          party = this.receiverName.trim();
        } else if (this.customerSearch && this.customerSearch.trim()) {
          party = this.customerSearch.trim();
        }

        let monthStr = 'ALL';
        let yearStr = 'ALL';
        if (this.selectedMonth) {
          const parts = this.selectedMonth.split('-');
          if (parts.length === 2) {
            const yearNum = Number(parts[0]);
            const monthNum = Number(parts[1]) - 1;
            const dateObj = new Date(yearNum, monthNum, 1);
            monthStr = dateObj.toLocaleString('en-US', { month: 'long' }).toUpperCase();
            yearStr = String(yearNum);
          }
        } else if (this.dateFrom) {
          const dateObj = new Date(this.dateFrom);
          monthStr = dateObj.toLocaleString('en-US', { month: 'long' }).toUpperCase();
          yearStr = String(dateObj.getFullYear());
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
