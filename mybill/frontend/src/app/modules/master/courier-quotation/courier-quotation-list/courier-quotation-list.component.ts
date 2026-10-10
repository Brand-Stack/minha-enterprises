import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { DatePipe } from '@angular/common';
import {
  courierCustomerDownloadFilename,
  filenameFromContentDisposition
} from '../../../../core/utils/content-disposition-filename.util';

interface CourierQuotation {
  id: string;
  quotationNumber: string;
  customerName: string;
  branchName: string;
  effectiveDate: string;
  validTillDate: string;
  status: string;
  createdBy: string;
  createdAt: string;
}

@Component({
  selector: 'app-courier-quotation-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Courier Rate Quotations</h1>
          <p class="body-small text-neutral-light">Manage courier rate quotations for customers</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" (keyup.enter)="applyFilters()" placeholder="Quotation no, customer, branch…">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field filter-field-wide">
            <mat-label>Effective From</mat-label>
            <input matInput [matDatepicker]="effFromPicker" [(ngModel)]="effectiveFrom" (keyup.enter)="applyFilters()">
            <mat-datepicker-toggle matSuffix [for]="effFromPicker"></mat-datepicker-toggle>
            <mat-datepicker #effFromPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field filter-field-wide">
            <mat-label>Effective To</mat-label>
            <input matInput [matDatepicker]="effToPicker" [(ngModel)]="effectiveTo" (keyup.enter)="applyFilters()">
            <mat-datepicker-toggle matSuffix [for]="effToPicker"></mat-datepicker-toggle>
            <mat-datepicker #effToPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field filter-field-status">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="statusFilter">
              <mat-option value="">All</mat-option>
              <mat-option value="DRAFT">Draft</mat-option>
              <mat-option value="APPROVED">Approved</mat-option>
              <mat-option value="ACTIVE">Active</mat-option>
              <mat-option value="EXPIRED">Expired</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="applyFilters()" matTooltip="Apply search and filters">
            <mat-icon>search</mat-icon>
            Search
          </button>
          <button *appHasPermission="'COURIER_QUOTATION:export'" mat-stroked-button (click)="exportAllQuotations()" [disabled]="loading || dataSource.data.length === 0" matTooltip="Export all displayed quotations to Excel">
            <mat-icon>table_view</mat-icon>
            <span>Export All Quotations</span>
          </button>
          <button *appHasPermission="'COURIER_QUOTATION:create'" mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Create Quotation</span>
          </button>
        </div>
      </div>

      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state"><app-loading-spinner></app-loading-spinner></div>
        <div *ngIf="!loading">
          <table mat-table [dataSource]="dataSource" class="data-table w-full">
            <ng-container matColumnDef="quotationNumber">
              <th mat-header-cell *matHeaderCellDef>Quotation No</th>
              <td mat-cell *matCellDef="let q"><strong>{{ q.quotationNumber }}</strong></td>
            </ng-container>
            <ng-container matColumnDef="customerName">
              <th mat-header-cell *matHeaderCellDef>Customer</th>
              <td mat-cell *matCellDef="let q">{{ q.customerName }}</td>
            </ng-container>
            <ng-container matColumnDef="branchName">
              <th mat-header-cell *matHeaderCellDef>Branch</th>
              <td mat-cell *matCellDef="let q">{{ q.branchName || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="effectiveDate">
              <th mat-header-cell *matHeaderCellDef>Effective</th>
              <td mat-cell *matCellDef="let q">{{ q.effectiveDate | date:'dd-MM-yyyy' }}</td>
            </ng-container>
            <ng-container matColumnDef="validTillDate">
              <th mat-header-cell *matHeaderCellDef>Valid Till</th>
              <td mat-cell *matCellDef="let q">{{ q.validTillDate | date:'dd-MM-yyyy' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let q">
                <span [class]="getStatusClass(q.status)">{{ q.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let q">
                <button mat-icon-button (click)="view(q.id)" matTooltip="Edit" [appDisableIfNoPermission]="'COURIER_QUOTATION:edit'">
                  <mat-icon>edit</mat-icon>
                </button>
                <button *appHasPermission="'COURIER_QUOTATION:print'" mat-icon-button (click)="print(q.id)" matTooltip="Print PDF">
                  <mat-icon>print</mat-icon>
                </button>
                <button *appHasPermission="'COURIER_QUOTATION:download'" mat-icon-button (click)="downloadPdf(q)" matTooltip="Download PDF">
                  <mat-icon>picture_as_pdf</mat-icon>
                </button>
                <button *appHasPermission="'COURIER_QUOTATION:delete'" mat-icon-button (click)="delete(q.id)" matTooltip="Delete" color="warn">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>
          <div *ngIf="!loading && dataSource.data.length === 0" class="empty-state">
            <mat-icon class="empty-icon">description</mat-icon>
            <p>No courier quotations found. Click "Create Quotation" to get started.</p>
          </div>
          <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize" [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons (page)="onPageChange($event)"></mat-paginator>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; color: #1f2937; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .header-actions { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
    .search-field { width: min(100%, 360px); min-width: 260px; }
    .filter-field { min-width: 160px; }
    .filter-field-wide { width: 180px; }
    .filter-field-status { width: 160px; }
    .table-card { padding: 0; }
    .data-table { width: 100%; }
    .loading-state, .empty-state { padding: 40px; text-align: center; color: #4b5563; }
    .text-neutral-light { color: #4b5563 !important; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; opacity: 0.3; margin-bottom: 12px; display: block; margin-left: auto; margin-right: auto; }
    .badge-draft    { background: #F3F4F6; color: #374151; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
    .badge-approved { background: #DBEAFE; color: #1E40AF; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
    .badge-active   { background: #D1FAE5; color: #065F46; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
    .badge-expired  { background: #FEE2E2; color: #991B1B; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
  `]
})
export class CourierQuotationListComponent implements OnInit {
  displayedColumns = ['quotationNumber', 'customerName', 'branchName', 'effectiveDate', 'validTillDate', 'status', 'actions'];
  dataSource = new MatTableDataSource<CourierQuotation>([]);
  loading = false;
  searchTerm = '';
  effectiveFrom: Date | null = null;
  effectiveTo: Date | null = null;
  statusFilter = '';

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toastService: ToastService,
    private http: HttpClient,
    private datePipe: DatePipe
  ) { }

  ngOnInit() {}

  ngAfterViewInit() {
    // Server-side pagination: do not assign dataSource.paginator so the table shows the current page data from API
    this.applyFilters();
  }

  totalElements = 0;
  page = 0;
  pageSize = 10;

  applyFilters() {
    this.page = 0;
    this.loadData();
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  loadData() {
    this.loading = true;
    const page = this.page;
    const size = this.pageSize;

    const params: Record<string, string | number> = {
      page,
      size,
      sortBy: 'effectiveDate',
      sortDir: 'desc'
    };
    const st = this.searchTerm?.trim();
    if (st) params['searchTerm'] = st;
    if (this.effectiveFrom) {
      const ef = this.datePipe.transform(this.effectiveFrom, 'yyyy-MM-dd');
      if (ef) params['effectiveFrom'] = ef;
    }
    if (this.effectiveTo) {
      const et = this.datePipe.transform(this.effectiveTo, 'yyyy-MM-dd');
      if (et) params['effectiveTo'] = et;
    }
    if (this.statusFilter?.trim()) params['status'] = this.statusFilter.trim();

    this.apiService.get<{ content: CourierQuotation[]; totalElements: number }>('/courier-quotations', params).subscribe({
      next: (res) => {
        this.dataSource.data = res?.content ?? [];
        this.totalElements = res?.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.dataSource.data = [];
        this.totalElements = 0;
        this.toastService.error('Error', 'Failed to load courier quotations');
        this.loading = false;
      }
    });
  }

  search() { this.applyFilters(); }

  createNew() { this.router.navigate(['/courier-quotations/create']); }
  view(id: string) { this.router.navigate(['/courier-quotations/edit', id]); }

  delete(id: string) {
    if (confirm('Delete this courier quotation?')) {
      this.apiService.delete('/courier-quotations', id).subscribe({
        next: () => { this.toastService.success('Success', 'Quotation deleted'); this.loadData(); },
        error: () => this.toastService.error('Error', 'Failed to delete quotation')
      });
    }
  }

  print(id: string) {
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    this.http.get(`${apiUrl}/courier-quotations/${id}/print`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        const url = window.URL.createObjectURL(res.body);
        const w = window.open(url, '_blank');
        if (w) { w.onload = () => { setTimeout(() => { w.print(); }, 500); }; }
        this.toastService.success('Success', 'PDF generated');
      },
      error: () => this.toastService.error('Error', 'Failed to generate PDF')
    });
  }

  downloadPdf(q: CourierQuotation) {
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    this.http.get(`${apiUrl}/courier-quotations/${q.id}/pdf`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        const fallback = courierCustomerDownloadFilename(q.customerName, 'pdf');
        const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
        const url = window.URL.createObjectURL(res.body);
        const a = document.createElement('a');
        a.href = url; a.download = name;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'PDF downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download PDF')
    });
  }

  exportAllQuotations() {
    this.toastService.info('Exporting', 'Generating Excel file...');
    const apiUrl = this.apiService.getBaseUrl();
    const params: any = {
      page: 0,
      size: 500,
      sortBy: 'effectiveDate',
      sortDir: 'desc'
    };
    const st = this.searchTerm?.trim();
    if (st) params.searchTerm = st;
    if (this.effectiveFrom) params.effectiveFrom = this.datePipe.transform(this.effectiveFrom, 'yyyy-MM-dd');
    if (this.effectiveTo) params.effectiveTo = this.datePipe.transform(this.effectiveTo, 'yyyy-MM-dd');
    if (this.statusFilter?.trim()) params.status = this.statusFilter.trim();
    const query = new URLSearchParams(params).toString();
    this.http.get(`${apiUrl}/courier-quotations/export/all?${query}`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        let filename = 'Quotations.xlsx';
        const cd = res.headers.get('content-disposition');
        if (cd) {
          const m = cd.match(/filename="?([^"]+)"?/);
          if (m?.[1]) filename = m[1];
        }
        const url = window.URL.createObjectURL(res.body);
        const a = document.createElement('a');
        a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'Excel downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to export Excel')
    });
  }

  getStatusClass(status: string): string {
    const map: any = { DRAFT: 'badge-draft', APPROVED: 'badge-approved', ACTIVE: 'badge-active', EXPIRED: 'badge-expired' };
    return map[status] || 'badge-draft';
  }
}
