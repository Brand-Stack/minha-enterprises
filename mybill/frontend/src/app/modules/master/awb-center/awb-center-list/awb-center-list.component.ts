import { Component, OnDestroy, OnInit, ViewChild, ElementRef, AfterViewInit, Inject } from '@angular/core';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { gsap } from 'gsap';
import { isoDateString } from '../../../../core/utils/list-filter-state.util';

interface AwbRow {
  id: string;
  awbNumber: string;
  queueStatus: string;
  courierType?: string;
  createdAt?: string;
  completedAt?: string;
  usedInModule?: string;
  collectionCustomerAwb?: boolean;
  lastUpdatedBy?: string;
}

interface CourierTypeStats {
  courierType: string;
  pendingCount: number;
  completedCount: number;
  totalCount: number;
}

interface CourierCardTheme {
  accent: string;
  bg: string;
  icon: string;
}

interface CourierTypeCard extends CourierTypeStats {
  theme: CourierCardTheme;
  active: boolean;
}

interface CollectionSummary {
  totalCount: number;
  mappedCount: number;
  unmappedCount: number;
  pendingCount: number;
  completedCount: number;
}

interface QueueStats {
  courierTypeStats?: CourierTypeStats[];
  collectionSummary?: CollectionSummary;
}

@Component({
  selector: 'app-awb-center-list',
  templateUrl: './awb-center-list.component.html',
  styleUrls: ['./awb-center-list.component.scss']
})
export class AwbCenterListComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(MatPaginator) paginator?: MatPaginator;
  @ViewChild('pageRoot') pageRoot?: ElementRef<HTMLElement>;

  rows: AwbRow[] = [];
  total = 0;
  page = 0;
  size = 25;
  loading = false;
  stats: QueueStats = {};

  awbSearch = '';
  queueFilter = '';
  typeFilter = '';
  courierTypeFilter = '';
  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  deletingAllPending = false;

  courierOptions: string[] = [];
  bulkCourierType = '';
  singleCourierType = '';

  bulkStart = '';
  bulkEnd = '';
  singleAwb = '';
  bulkGenerating = false;
  syncing = false;

  bulkCollectionCustomerAwb = false;
  singleCollectionCustomerAwb = false;

  private readonly courierCardThemes: CourierCardTheme[] = [
    { accent: '#4f46e5', bg: 'linear-gradient(135deg, #eef2ff 0%, #fff 100%)', icon: 'local_shipping' },
    { accent: '#0ea5e9', bg: 'linear-gradient(135deg, #f0f9ff 0%, #fff 100%)', icon: 'flight_takeoff' },
    { accent: '#10b981', bg: 'linear-gradient(135deg, #ecfdf5 0%, #fff 100%)', icon: 'inventory_2' },
    { accent: '#f59e0b', bg: 'linear-gradient(135deg, #fffbeb 0%, #fff 100%)', icon: 'rocket_launch' },
    { accent: '#8b5cf6', bg: 'linear-gradient(135deg, #f5f3ff 0%, #fff 100%)', icon: 'delivery_dining' },
    { accent: '#ec4899', bg: 'linear-gradient(135deg, #fdf2f8 0%, #fff 100%)', icon: 'package_2' }
  ];

  private readonly reduceMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private companySettingsLists: CompanySettingsListsService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.companySettingsLists.getLists().subscribe({
      next: (s) => (this.courierOptions = s.couriers || []),
      error: () => (this.courierOptions = [])
    });
    this.loadStats();
    this.load(0);
  }

  ngAfterViewInit(): void {
    if (this.reduceMotion || !this.pageRoot) return;
    gsap.from(this.pageRoot.nativeElement.querySelectorAll('.awb-animate'), {
      opacity: 0,
      y: 16,
      duration: 0.45,
      stagger: 0.06,
      ease: 'power2.out'
    });
  }

  ngOnDestroy(): void {}

  collectionSummary(): CollectionSummary {
    return this.stats.collectionSummary || {
      totalCount: 0,
      mappedCount: 0,
      unmappedCount: 0,
      pendingCount: 0,
      completedCount: 0
    };
  }

  courierTypeCards(): CourierTypeCard[] {
    const byType = new Map<string, CourierTypeStats>();
    for (const s of this.stats.courierTypeStats || []) {
      if (s.courierType) {
        byType.set(s.courierType, s);
      }
    }
    const names = new Set<string>(this.courierOptions);
    for (const s of this.stats.courierTypeStats || []) {
      if (s.courierType) names.add(s.courierType);
    }
    return Array.from(names).map((name, idx) => {
      const stat = byType.get(name) || { courierType: name, pendingCount: 0, completedCount: 0, totalCount: 0 };
      return {
        ...stat,
        theme: this.courierCardThemes[idx % this.courierCardThemes.length],
        active: this.courierTypeFilter === name
      };
    });
  }

  activeChips(): { key: string; label: string }[] {
    const chips: { key: string; label: string }[] = [];
    if (this.awbSearch.trim()) chips.push({ key: 'awb', label: 'AWB: ' + this.awbSearch.trim() });
    if (this.queueFilter) chips.push({ key: 'queue', label: 'Status: ' + this.queueFilter });
    if (this.typeFilter) {
      const typeLabel = this.typeFilter === 'COLLECTION' ? 'Collection' : 'Standard';
      chips.push({ key: 'type', label: 'AWB Type: ' + typeLabel });
    }
    if (this.courierTypeFilter) chips.push({ key: 'courier', label: 'Courier: ' + this.courierTypeFilter });
    if (this.dateFrom) chips.push({ key: 'from', label: 'From: ' + isoDateString(this.dateFrom) });
    if (this.dateTo) chips.push({ key: 'to', label: 'To: ' + isoDateString(this.dateTo) });
    return chips;
  }

  removeChip(key: string): void {
    switch (key) {
      case 'awb': this.awbSearch = ''; break;
      case 'queue': this.queueFilter = ''; break;
      case 'type': this.typeFilter = ''; break;
      case 'courier': this.courierTypeFilter = ''; break;
      case 'from': this.dateFrom = null; break;
      case 'to': this.dateTo = null; break;
    }
    this.searchAwbs();
  }

  private params(): Record<string, string | number> {
    const p: Record<string, string | number> = {
      page: this.page,
      size: this.size,
      sortBy: 'awbNumber',
      sortDir: 'asc'
    };
    if (this.awbSearch.trim()) p['awbNumber'] = this.awbSearch.trim();
    if (this.queueFilter) p['queueStatus'] = this.queueFilter;
    if (this.typeFilter) p['awbType'] = this.typeFilter;
    if (this.courierTypeFilter) p['courierType'] = this.courierTypeFilter;
    const df = isoDateString(this.dateFrom);
    const dt = isoDateString(this.dateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;
    return p;
  }

  load(pageIdx: number): void {
    this.page = pageIdx;
    this.loading = true;
    this.api.get<PageResponse<AwbRow>>('/awb-center', this.params()).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.total = res?.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Error', 'Failed to load AWB Center');
      }
    });
  }

  searchAwbs(): void {
    this.load(0);
  }

  loadStats(): void {
    this.api.get<QueueStats>('/awb-center/stats').subscribe({
      next: (s) => (this.stats = s || {}),
      error: () => {}
    });
  }

  filterByCourierType(name: string): void {
    this.courierTypeFilter = this.courierTypeFilter === name ? '' : name;
    this.searchAwbs();
  }

  onPage(e: PageEvent): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  reset(): void {
    this.awbSearch = '';
    this.queueFilter = '';
    this.typeFilter = '';
    this.courierTypeFilter = '';
    this.dateFrom = null;
    this.dateTo = null;
    this.searchAwbs();
  }

  deleteAllPending(): void {
    const ok = confirm(
      'Delete ALL pending AWBs from AWB Center and Collection Customer mappings?\n\nCompleted and used AWBs will NOT be deleted.'
    );
    if (!ok) return;
    this.deletingAllPending = true;
    this.api.post<{ deletedCount: number; message: string }>('/awb-center/delete-all-pending', {}).subscribe({
      next: (res) => {
        this.deletingAllPending = false;
        const n = Number(res?.deletedCount ?? 0);
        const msg = res?.message || (n > 0 ? `${n} pending AWBs deleted successfully` : 'No pending AWBs to delete');
        if (n > 0) this.toast.success('Deleted', msg);
        else this.toast.info('Delete', msg);
        this.load(this.page);
        this.loadStats();
      },
      error: (e) => {
        this.deletingAllPending = false;
        this.toast.error('Error', e?.error?.message || 'Could not delete pending AWBs');
      }
    });
  }

  addSingle(): void {
    const awb = (this.singleAwb || '').trim();
    if (!awb) {
      this.toast.warning('AWB', 'Enter an AWB number');
      return;
    }
    this.api.post<AwbRow>('/awb-center', {
      awbNumber: awb,
      collectionCustomerAwb: this.singleCollectionCustomerAwb,
      courierType: this.singleCourierType || undefined
    }).subscribe({
      next: () => {
        this.singleAwb = '';
        this.toast.success('Success', 'AWB added');
        this.load(0);
        this.loadStats();
      },
      error: (e) => this.toast.error('Error', e?.error?.message || 'Could not add AWB')
    });
  }

  bulkGenerate(): void {
    const start = (this.bulkStart || '').trim();
    const end = (this.bulkEnd || '').trim();
    if (!start || !end) {
      this.toast.warning('Bulk', 'Enter start and end AWB');
      return;
    }
    this.bulkGenerating = true;
    this.api.post<{ createdCount: number; message: string }>('/awb-center/bulk-generate', {
      startAwb: start,
      endAwb: end,
      collectionCustomerAwb: this.bulkCollectionCustomerAwb,
      courierType: this.bulkCourierType || undefined
    }).subscribe({
      next: (res) => {
        this.bulkGenerating = false;
        this.toast.success('Generated', res?.message || `${res?.createdCount ?? 0} AWB(s) created`);
        this.load(0);
        this.loadStats();
      },
      error: (e) => {
        this.bulkGenerating = false;
        this.toast.error('Error', e?.error?.message || 'Bulk generation failed');
      }
    });
  }

  syncQueues(): void {
    this.syncing = true;
    this.api.post<{ updatedCount: number }>('/awb-center/sync-queues', {}).subscribe({
      next: (res) => {
        this.syncing = false;
        this.toast.success('Sync', `${res?.updatedCount ?? 0} AWB(s) moved to completed`);
        this.load(this.page);
        this.loadStats();
      },
      error: () => {
        this.syncing = false;
        this.toast.error('Error', 'Queue sync failed');
      }
    });
  }

  deleteRow(row: AwbRow): void {
    if (!confirm(`Delete AWB ${row.awbNumber}?`)) return;
    this.api.delete('/awb-center', row.id).subscribe({
      next: () => {
        this.toast.success('Deleted', '');
        this.load(this.page);
        this.loadStats();
      },
      error: () => this.toast.error('Error', 'Delete failed')
    });
  }

  statusClass(status: string): string {
    return status === 'COMPLETED' ? 'awb-badge awb-badge--done' : 'awb-badge awb-badge--pending';
  }

  openPendingDialog(courierType?: string): void {
    const subtitle = courierType === 'COLLECTION'
      ? 'All pending collection-type AWBs'
      : (courierType ? `All pending standard AWBs for ${courierType}` : 'All pending AWBs');
    this.dialog.open(AwbDetailDialogComponent, {
      width: '750px',
      maxWidth: '95vw',
      data: {
        title: courierType ? `Pending AWBs (${courierType})` : 'Pending AWBs',
        subtitle: subtitle,
        mode: 'PENDING',
        courierType: courierType
      }
    });
  }

  openUnmappedDialog(): void {
    this.dialog.open(AwbDetailDialogComponent, {
      width: '750px',
      maxWidth: '95vw',
      data: {
        title: 'Unmapped Collection AWBs',
        subtitle: 'Collection AWBs not mapped to any collection customer',
        mode: 'UNMAP'
      }
    });
  }
}

@Component({
  selector: 'app-awb-detail-dialog',
  template: `
    <div class="awbd-dialog">
      <header class="awbd-header">
        <div>
          <h2>{{ data.title }}</h2>
          <p class="awbd-sub">{{ data.subtitle }}</p>
        </div>
        <button mat-icon-button (click)="close()"><mat-icon>close</mat-icon></button>
      </header>

      <div class="awbd-toolbar">
        <mat-form-field appearance="outline" class="awbd-search">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search AWB..." [(ngModel)]="searchVal" (keyup.enter)="loadData(0)">
        </mat-form-field>
        <div class="awbd-actions">
          <button mat-stroked-button (click)="exportExcel()"><mat-icon>table_chart</mat-icon> Export Excel</button>
          <button mat-stroked-button (click)="printList()"><mat-icon>print</mat-icon> Print</button>
        </div>
      </div>

      <div class="awbd-table-wrap">
        <div class="awbd-loading" *ngIf="loading"><mat-spinner diameter="36"></mat-spinner></div>
        <table class="awbd-table" *ngIf="!loading">
          <thead>
            <tr>
              <th>AWB No</th>
              <th>Type</th>
              <th>Courier</th>
              <th>Customer</th>
              <th>Created Date</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of rows">
              <td class="awbd-mono">{{ r.awbNumber }}</td>
              <td>
                <span class="awbd-badge" [class.awbd-badge--collection]="r.collectionCustomerAwb">
                  {{ r.collectionCustomerAwb ? 'Collection' : 'Standard' }}
                </span>
              </td>
              <td>{{ r.courierType || '—' }}</td>
              <td>{{ r.collectionCustomerName || '—' }}</td>
              <td>{{ r.createdAt | date:'yyyy-MM-dd HH:mm' }}</td>
              <td>
                <span class="awbd-badge" [class.awbd-badge--done]="r.queueStatus === 'COMPLETED'">
                  {{ r.queueStatus }}
                </span>
              </td>
            </tr>
            <tr *ngIf="!rows.length">
              <td colspan="6" class="awbd-empty">No records found</td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="awbd-footer">
        <mat-paginator [length]="total" [pageSize]="size" [pageIndex]="page" [pageSizeOptions]="[10, 25, 50]" (page)="onPage($event)"></mat-paginator>
      </footer>
    </div>
  `,
  styles: [`
    .awbd-dialog { padding: 24px; display: flex; flex-direction: column; gap: 16px; min-width: 600px; max-width: 90vw; }
    .awbd-header { display: flex; justify-content: space-between; align-items: flex-start; }
    .awbd-header h2 { margin: 0; font-size: 20px; font-weight: 700; color: #0f172a; }
    .awbd-sub { margin: 4px 0 0; font-size: 13px; color: #64748b; }
    .awbd-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
    .awbd-search { flex: 1; min-width: 200px; margin-bottom: -1.25em; }
    .awbd-actions { display: flex; gap: 8px; }
    .awbd-table-wrap { min-height: 200px; max-height: 400px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px; position: relative; }
    .awbd-loading { position: absolute; inset: 0; background: rgba(255,255,255,0.7); display: flex; align-items: center; justify-content: center; z-index: 2; }
    .awbd-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .awbd-table th { background: #f8fafc; padding: 10px 12px; font-weight: 600; text-align: left; border-bottom: 2px solid #e2e8f0; color: #475569; }
    .awbd-table td { padding: 10px 12px; border-bottom: 1px solid #e2e8f0; color: #334155; }
    .awbd-mono { font-family: monospace; font-weight: 600; }
    .awbd-badge { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; background: #e2e8f0; color: #475569; }
    .awbd-badge--collection { background: #fee2e2; color: #991b1b; }
    .awbd-badge--done { background: #d1fae5; color: #065f46; }
    .awbd-empty { text-align: center; color: #94a3b8; padding: 32px !important; }
    .awbd-footer { margin-top: 8px; }
  `]
})
export class AwbDetailDialogComponent implements OnInit {
  rows: any[] = [];
  total = 0;
  page = 0;
  size = 10;
  loading = false;
  searchVal = '';

  constructor(
    public dialogRef: MatDialogRef<AwbDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { title: string; subtitle: string; mode: string; courierType?: string },
    private api: ApiService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadData(0);
  }

  loadData(pageIdx: number): void {
    this.page = pageIdx;
    this.loading = true;
    const params: any = {
      page: this.page,
      size: this.size,
      sortBy: 'awbNumber',
      sortDir: 'asc'
    };
    if (this.searchVal.trim()) {
      params.awbNumber = this.searchVal.trim();
    }
    if (this.data.mode === 'PENDING') {
      params.queueStatus = 'PENDING';
      if (this.data.courierType) {
        if (this.data.courierType === 'COLLECTION') {
          params.awbType = 'COLLECTION';
        } else {
          params.courierType = this.data.courierType;
        }
      }
    } else if (this.data.mode === 'UNMAP') {
      params.unmapped = 'true';
    }

    this.api.get<PageResponse<any>>('/awb-center', params).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.total = res?.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.error('Error', 'Failed to load list details');
      }
    });
  }

  onPage(e: any): void {
    this.size = e.pageSize;
    this.loadData(e.pageIndex);
  }

  close(): void {
    this.dialogRef.close();
  }

  exportExcel(): void {
    const headers = ['AWB No', 'Type', 'Courier', 'Customer', 'Created Date', 'Status'];
    const lines = this.rows.map(r => [
      r.awbNumber || '',
      r.collectionCustomerAwb ? 'Collection' : 'Standard',
      r.courierType || '',
      r.collectionCustomerName || '',
      r.createdAt || '',
      r.queueStatus || ''
    ].join(','));
    const csv = [headers.join(','), ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.data.title.toLowerCase().replace(/[^a-z0-9]+/g, '_')}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  printList(): void {
    const printContent = document.createElement('div');
    printContent.innerHTML = `
      <h2>${this.data.title}</h2>
      <p>${this.data.subtitle}</p>
      <table border="1" cellpadding="8" style="border-collapse: collapse; width: 100%;">
        <thead>
          <tr>
            <th>AWB No</th><th>Type</th><th>Courier</th><th>Customer</th><th>Created</th><th>Status</th>
          </tr>
        </thead>
        <tbody>
          ${this.rows.map(r => `
            <tr>
              <td>${r.awbNumber || ''}</td>
              <td>${r.collectionCustomerAwb ? 'Collection' : 'Standard'}</td>
              <td>${r.courierType || '—'}</td>
              <td>${r.collectionCustomerName || '—'}</td>
              <td>${r.createdAt || ''}</td>
              <td>${r.queueStatus || ''}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;
    const frame = document.createElement('iframe');
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
    document.body.appendChild(frame);
    const doc = frame.contentWindow?.document;
    if (doc) {
      doc.write(printContent.innerHTML);
      doc.close();
      frame.contentWindow?.focus();
      frame.contentWindow?.print();
    }
    setTimeout(() => document.body.removeChild(frame), 60000);
  }
}
