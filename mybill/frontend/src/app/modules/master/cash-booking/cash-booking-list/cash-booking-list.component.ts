import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import {
  clearListFilterState,
  isoDateString,
  loadListFilterState,
  parseIsoDate,
  saveListFilterState
} from '../../../../core/utils/list-filter-state.util';

const CASH_BOOKING_LIST_FILTER_KEY = 'cash_booking_list_filters';

@Component({
  selector: 'app-cash-booking-list',
  templateUrl: './cash-booking-list.component.html',
  styleUrls: ['./cash-booking-list.component.scss']
})
export class CashBookingListComponent implements OnInit {
  rows: any[] = [];
  total = 0;
  page = 0;
  size = 20;
  loading = false;
  totals: { totalRecords: number; totalAmount: number } | null = null;

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
  amountStatusOptions = ['Cash', 'GPay', 'Pending', 'COD', 'Paid', 'UnPaid'];
  courierOptions: string[] = [];
  statusOptions: string[] = [];

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private router: Router,
    private companyLists: CompanySettingsListsService
  ) {}

  ngOnInit(): void {
    this.companyLists.getLists().subscribe((lists) => {
      this.courierOptions = lists.couriers || [];
      this.statusOptions = lists.statuses || [];
    });
    this.restoreFilters();
    this.load(this.page);
  }

  private persistFilters(): void {
    saveListFilterState(CASH_BOOKING_LIST_FILTER_KEY, {
      dateFrom: isoDateString(this.dateFrom),
      dateTo: isoDateString(this.dateTo),
      awbNo: this.awbNo,
      receiverName: this.receiverName,
      pincode: this.pincode,
      state: this.state,
      areaName: this.areaName,
      courier: this.courier,
      status: this.status,
      amountStatus: this.amountStatus,
      remarks: this.remarks,
      page: this.page,
      size: this.size
    });
  }

  private restoreFilters(): void {
    const saved = loadListFilterState<Record<string, unknown>>(CASH_BOOKING_LIST_FILTER_KEY);
    if (!saved) return;
    this.dateFrom = parseIsoDate(saved['dateFrom']);
    this.dateTo = parseIsoDate(saved['dateTo']);
    if (saved['awbNo'] != null) this.awbNo = String(saved['awbNo']);
    if (saved['receiverName'] != null) this.receiverName = String(saved['receiverName']);
    if (saved['pincode'] != null) this.pincode = String(saved['pincode']);
    if (saved['state'] != null) this.state = String(saved['state']);
    if (saved['areaName'] != null) this.areaName = String(saved['areaName']);
    if (saved['courier'] != null) this.courier = String(saved['courier']);
    if (saved['status'] != null) this.status = String(saved['status']);
    if (saved['amountStatus'] != null) this.amountStatus = String(saved['amountStatus']);
    if (saved['remarks'] != null) this.remarks = String(saved['remarks']);
    if (typeof saved['page'] === 'number') this.page = saved['page'];
    if (typeof saved['size'] === 'number') this.size = saved['size'];
  }

  private iso(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private params(): Record<string, string | number> {
    const p: Record<string, string | number> = { page: this.page, size: this.size, sortBy: 'bookingDate', sortDir: 'desc' };
    const df = this.iso(this.dateFrom);
    const dt = this.iso(this.dateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;
    if (this.awbNo.trim()) p['awbNo'] = this.awbNo.trim();
    if (this.receiverName.trim()) p['receiverName'] = this.receiverName.trim();
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
    this.persistFilters();
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
        this.toast.error('Error', 'Failed to load cash bookings');
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
    clearListFilterState(CASH_BOOKING_LIST_FILTER_KEY);
    this.load(0);
  }

  onPage(e: any): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  create(): void {
    this.persistFilters();
    this.router.navigate(['/cash-booking/create']);
  }

  edit(r: any): void {
    this.persistFilters();
    this.router.navigate(['/cash-booking/edit', r.id]);
  }

  deleteRow(r: any): void {
    if (!confirm(`Delete cash booking for ${r.receiverName || 'this row'}?`)) {
      return;
    }
    this.api.deletePath(`/cash-bookings/${r.id}`).subscribe({
      next: () => {
        this.toast.success('Deleted', '');
        this.load(this.page);
      },
      error: (e) => {
        const msg = e?.error?.message || 'Delete failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Delete failed');
      }
    });
  }

  exportCsv(): void {
    const p = { ...this.params(), page: 0, size: 5000 };
    this.api.get<PageResponse<any>>('/cash-bookings/search', p).subscribe({
      next: (res) => {
        const list = res?.content ?? [];
        const esc = (v: string) => '"' + String(v ?? '').replace(/"/g, '""') + '"';
        const lines = [
          ['Date', 'Receiver', 'Pincode', 'State', 'City', 'Area', 'AWB', 'Courier', 'Weight', 'Item', 'Status', 'Amount', 'Amt status', 'Remarks', 'Address'].join(','),
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
              x.weight ?? '',
              esc(x.item ?? ''),
              esc(x.status ?? ''),
              x.amount ?? '',
              esc(x.amountStatus ?? ''),
              esc(x.remarks ?? ''),
              esc(x.fullAddress ?? '')
            ].join(',')
          )
        ];
        const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'cash-bookings.csv';
        a.click();
        URL.revokeObjectURL(a.href);
        this.toast.success('Export', `${list.length} row(s)`);
      },
      error: () => this.toast.error('Error', 'Export failed')
    });
  }
}
