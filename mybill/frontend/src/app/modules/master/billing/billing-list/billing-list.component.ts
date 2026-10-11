import { Component, OnInit, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { trigger, transition, style, animate } from '@angular/animations';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { AnimationService } from '../../../../core/services/animation.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { environment } from '../../../../../environments/environment';

export interface Billing {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceDateTime?: string; // Full timestamp with hours:minutes:seconds
  partyId?: string;
  partyName: string;
  totalAmount: number;
  paymentStatus: string;
  billType?: string; // ESTIMATE or GST
  status?: string; // NORMAL or CORRECTED
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-billing-list',
  template: `
    <div class="page-container" [@fadeSlideUp]>
      <!-- Page Header -->
      <div class="page-header">
        <div>
          <h1 class="h2">Transaction</h1>
          <p class="body-small text-neutral-light">Manage invoices and bills</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Party</mat-label>
            <mat-select [(value)]="selectedParty" (selectionChange)="applyFilters()">
              <mat-option value="">All Parties</mat-option>
              <mat-option *ngFor="let party of parties" [value]="party.id">{{ party.partyName }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Invoice Number</mat-label>
            <input matInput [(ngModel)]="invoiceNumberFilter" 
                   (keyup.enter)="applyFilters()" 
                   (blur)="applyFilters()"
                   placeholder="Search invoice number...">
          </mat-form-field>
          <button mat-icon-button (click)="clearFilters()" matTooltip="Clear Filters" *ngIf="hasActiveFilters()">
            <mat-icon>clear</mat-icon>
          </button>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Invoice Type</mat-label>
            <mat-select [(value)]="selectedType" (selectionChange)="applyFilters()">
              <mat-option value="">All Types</mat-option>
              <mat-option value="ESTIMATE">Estimate</mat-option>
              <mat-option value="GST">Invoice</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select [(value)]="selectedStatus" (selectionChange)="applyFilters()">
              <mat-option value="">All Status</mat-option>
              <mat-option value="NORMAL">Normal</mat-option>
              <mat-option value="DRAFT">Draft</mat-option>
              <mat-option value="CORRECTED">Corrected</mat-option>
              <mat-option value="RETURNED">Returned</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-raised-button (click)="downloadExcel()" [disabled]="loading" class="excel-btn">
            <mat-icon>table_chart</mat-icon>
            <span>Export Excel</span>
          </button>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Add Transaction</span>
          </button>
        </div>
      </div>

      <!-- Data Table Card -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state">
          <app-loading-spinner></app-loading-spinner>
        </div>
        
        <div *ngIf="!loading && dataSource.data.length > 0">
          <table mat-table [dataSource]="dataSource" class="data-table">
            <!-- Invoice Number Column -->
            <ng-container matColumnDef="invoiceNumber">
              <th mat-header-cell *matHeaderCellDef class="table-header">Invoice No</th>
              <td mat-cell *matCellDef="let billing" class="table-cell mono">
                {{ billing.invoiceNumber || '—' }}
              </td>
            </ng-container>

            <!-- Date Column -->
            <ng-container matColumnDef="invoiceDate">
              <th mat-header-cell *matHeaderCellDef class="table-header">Date</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">
                {{ getFormattedDate(billing) }}
              </td>
            </ng-container>

            <!-- Party Column -->
            <ng-container matColumnDef="partyName">
              <th mat-header-cell *matHeaderCellDef class="table-header">Party</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">{{ billing.partyName }}</td>
            </ng-container>

            <!-- Type Column -->
            <ng-container matColumnDef="billType">
              <th mat-header-cell *matHeaderCellDef class="table-header">Type</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">
                <span class="badge-status" [ngClass]="getTypeBadgeClass(billing.billType)">
                  {{ billing.billType || 'N/A' }}
                </span>
              </td>
            </ng-container>

            <!-- Status Column -->
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef class="table-header">Status</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">
                <span class="badge-status" [ngClass]="getStatusBadgeClass(billing.status)">
                  {{ billing.status || 'NORMAL' }}
                </span>
              </td>
            </ng-container>

            <!-- Amount Column -->
            <ng-container matColumnDef="totalAmount">
              <th mat-header-cell *matHeaderCellDef class="table-header">Total Amount</th>
              <td mat-cell *matCellDef="let billing" class="table-cell" style="min-width: 120px; text-align: right;">
                ₹{{ billing.totalAmount || 0 | number:'1.2-2' }}
              </td>
            </ng-container>

            <!-- Payment Status Column -->
            <ng-container matColumnDef="paymentStatus">
              <th mat-header-cell *matHeaderCellDef class="table-header">Payment</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">
                <span *ngIf="billing.billType !== 'ESTIMATE'" class="badge-status" [ngClass]="getPaymentBadgeClass(billing.paymentStatus)">
                  {{ billing.paymentStatus }}
                </span>
                <span *ngIf="billing.billType === 'ESTIMATE'" class="text-gray-400">—</span>
              </td>
            </ng-container>

            <!-- Actions Column -->
            <ng-container matColumnDef="lastUpdatedBy">
              <th mat-header-cell *matHeaderCellDef class="table-header">Last Updated By</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">{{ billing.lastUpdatedBy || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="table-header">Actions</th>
              <td mat-cell *matCellDef="let billing" class="table-cell">
                <div class="action-buttons">
                  <button mat-icon-button (click)="view(billing.id)" 
                          class="btn-icon"
                          matTooltip="View">
                    <mat-icon>visibility</mat-icon>
                  </button>
                  <button mat-icon-button (click)="edit(billing.id)" 
                          class="btn-icon"
                          matTooltip="Edit">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button (click)="downloadPdf(billing.id)" 
                          class="btn-icon"
                          matTooltip="Download PDF">
                    <mat-icon>download</mat-icon>
                  </button>
                  <button mat-icon-button (click)="delete(billing.id)" 
                          class="btn-icon btn-icon-danger"
                          matTooltip="Delete">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns" class="table-header-row"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns" 
                class="table-row"
                [ngClass]="{'row-returned': row.status === 'RETURNED'}"
                [@rowAnimation]></tr>
          </table>

          <mat-paginator [length]="totalElements" 
                         [pageIndex]="page"
                         [pageSize]="pageSize" 
                         [pageSizeOptions]="[5, 10, 25, 50]"
                         showFirstLastButtons
                         (page)="onPageChange($event)"
                         class="table-paginator"></mat-paginator>
        </div>

        <!-- Empty State -->
        <div *ngIf="!loading && dataSource.data.length === 0" class="empty-state">
          <div class="empty-illustration">
            <mat-icon>receipt</mat-icon>
          </div>
          <h3 class="empty-title">No Bills Found</h3>
          <p class="empty-description">Get started by creating your first bill</p>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Add Transaction</span>
          </button>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container {
      animation: fadeSlideUp 0.3s ease-out;
    }

    /* Page Header */
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      gap: 16px;
      flex-wrap: wrap;
    }

    .page-header h1 {
      margin-bottom: 4px;
      color: #1A1D2E;
    }

    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
    }

    .excel-btn {
      background-color: #217346;
      color: white;
    }
    .excel-btn:hover {
      background-color: #1a5c38;
    }

    .filter-field {
      width: 200px;
    }

    /* Table Card */
    .table-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #E5E7EB;
      overflow: hidden;
    }

    .loading-state {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 64px;
    }

    /* Data Table */
    .data-table {
      width: 100%;
    }

    .table-header-row {
      background: #F9FAFB !important;
    }

    .table-header {
      font-weight: 600 !important;
      font-size: 12px !important;
      text-transform: uppercase !important;
      color: #6B7280 !important;
      padding: 12px 16px !important;
      border-bottom: 1px solid #E5E7EB !important;
    }

    .table-row {
      padding: 16px !important;
      border-bottom: 1px solid #F3F4F6 !important;
      transition: background-color 0.2s ease;
    }

    .table-row:hover {
      background: #F9FAFB !important;
    }

    .table-row.row-returned {
      background: #FFF7ED !important;
      border-left: 3px solid #F97316;
    }

    .table-row.row-returned:hover {
      background: #FFEDD5 !important;
    }

    .table-row:last-child {
      border-bottom: none !important;
    }

    .table-cell {
      font-size: 14px !important;
      color: #374151 !important;
      padding: 12px 16px !important;
    }

    /* Action Buttons */
    .action-buttons {
      display: flex;
      gap: 4px;
    }

    .btn-icon {
      width: 36px;
      height: 36px;
      border-radius: 6px;
      color: #6B7280;
      transition: all 0.2s ease;
    }

    .btn-icon:hover {
      background: #F3F4F6;
      color: #5B6FE8;
    }

    .btn-icon:active {
      transform: scale(0.95);
    }

    .btn-icon-danger:hover {
      color: #EF4444;
      background: #FEE2E2;
    }

    /* Badges */
    .badge-status {
      display: inline-flex;
      align-items: center;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
    }

    .badge-success {
      background: #D1FAE5;
      color: #065F46;
    }

    .badge-warning {
      background: #FEF3C7;
      color: #92400E;
    }

    .badge-draft {
      background: #E0E7FF;
      color: #3730A3;
    }

    .badge-returned {
      background: #FED7AA;
      color: #C2410C;
    }

    .badge-info {
      background: #DBEAFE;
      color: #1E40AF;
    }

    .badge-estimate {
      background: #E0E7FF;
      color: #3730A3;
    }

    .badge-gst {
      background: #D1FAE5;
      color: #065F46;
    }

    /* Payment Status */
    .badge-paid {
      background: #D1FAE5;
      color: #065F46;
    }

    .badge-pending {
      background: #FEF3C7;
      color: #92400E;
    }

    .badge-partial {
      background: #DBEAFE;
      color: #1E40AF;
    }

    /* Paginator */
    .table-paginator {
      border-top: 1px solid #F3F4F6;
    }

    /* Empty State */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
    }

    .empty-illustration {
      width: 120px;
      height: 120px;
      border-radius: 50%;
      background: #F3F4F6;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 24px;
    }

    .empty-illustration mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #9CA3AF;
    }

    .empty-title {
      font-size: 16px;
      font-weight: 600;
      color: #1A1D2E;
      margin-bottom: 8px;
    }

    .empty-description {
      font-size: 14px;
      color: #6B7280;
      margin-bottom: 24px;
    }

    /* Utility classes */
    .text-neutral-light {
      color: #6B7280;
    }

    .mono {
      font-family: 'Roboto Mono', monospace;
      font-size: 13px;
    }
  `],
  animations: [
    trigger('fadeSlideUp', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('0.3s ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ]),
    trigger('rowAnimation', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(-10px)' }),
        animate('0.2s ease-out', style({ opacity: 1, transform: 'translateX(0)' }))
      ])
    ])
  ]
})
export class BillingListComponent implements OnInit, AfterViewInit {
  displayedColumns = ['invoiceNumber', 'invoiceDate', 'partyName', 'billType', 'status', 'totalAmount', 'paymentStatus', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<Billing>([]);
  totalElements = 0;
  page = 0;
  pageSize = 10;
  selectedType = '';
  selectedParty = '';
  selectedStatus = '';
  invoiceNumberFilter = '';
  parties: any[] = [];
  loading = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('pageContainer') pageContainer!: ElementRef;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private animationService: AnimationService,
    private toastService: ToastService,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.loadParties();
    this.loadBillings();
  }
  
  loadParties() {
    this.apiService.getPaged<any>('/clients', 0, 1000).subscribe({
      next: (response) => {
        this.parties = response.content;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load parties');
      }
    });
  }

  ngAfterViewInit() {
    if (this.pageContainer) {
      this.animationService.fadeIn(this.pageContainer.nativeElement, 0.3);
    }
  }

  loadBillings() {
    this.loading = true;
    const params: any = {
      page: this.page,
      size: this.pageSize,
      sortBy: 'invoiceDate',
      sortDir: 'desc'
    };
    
    // Build query parameters - only include non-empty filters
    if (this.selectedType && this.selectedType.trim()) {
      params.billType = this.selectedType.trim();
    }
    if (this.selectedParty && this.selectedParty.trim()) {
      params.partyId = this.selectedParty.trim();
    }
    if (this.invoiceNumberFilter && this.invoiceNumberFilter.trim()) {
      params.invoiceNumber = this.invoiceNumberFilter.trim();
    }
    if (this.selectedStatus && this.selectedStatus.trim()) {
      params.status = this.selectedStatus.trim();
    }
    
    // Use the backend endpoint with filters
    this.apiService.get<PageResponse<Billing>>('/invoices', params)
      .subscribe({
        next: (response) => {
          if (response && response.content) {
            this.dataSource.data = response.content;
            this.totalElements = response.totalElements || 0;
          } else {
            this.dataSource.data = [];
            this.totalElements = 0;
          }
          this.loading = false;
        },
        error: (error) => {
          this.loading = false;
          this.toastService.error('Error', 'Failed to load billings');
          console.error('Error loading billings:', error);
          // Set empty data on error
          this.dataSource.data = [];
          this.totalElements = 0;
        }
      });
  }

  applyFilters() {
    // Reset to first page when filters change
    this.page = 0;
    this.loadBillings();
  }

  filterByType() {
    this.applyFilters();
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadBillings();
  }

  createNew() {
    this.router.navigate(['/master/billing/create']);
  }

  view(id: string) {
    this.router.navigate(['/master/billing/view', id]);
  }

  edit(id: string) {
    this.router.navigate(['/master/billing/edit', id]);
  }

  delete(id: string) {
    if (confirm('Are you sure you want to permanently delete this bill? This action cannot be undone.')) {
      this.apiService.delete('/invoices', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Bill deleted successfully');
          this.loadBillings();
        },
        error: () => {
          this.toastService.error('Error', 'Failed to delete bill');
        }
      });
    }
  }

  downloadExcel() {
    this.toastService.info('Downloading', 'Generating Excel...');
    const apiUrl = this.apiService.getBaseUrl();
    const params = new URLSearchParams();
    if (this.selectedType) params.set('billType', this.selectedType);
    if (this.selectedParty) params.set('partyId', this.selectedParty);
    if (this.invoiceNumberFilter) params.set('invoiceNumber', this.invoiceNumberFilter);
    if (this.selectedStatus) params.set('status', this.selectedStatus);
    const query = params.toString();
    const url = `${apiUrl}/invoices/export${query ? '?' + query : ''}`;
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No Excel data received');
          return;
        }
        const urlObj = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = urlObj;
        link.download = 'transactions_export.xlsx';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(urlObj);
        this.toastService.success('Success', 'Excel downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download Excel');
      }
    });
  }

  downloadPdf(id: string) {
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    // Use HttpClient directly for blob response with full response headers
    this.http.get(`${apiUrl}/invoices/${id}/pdf`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        // Extract filename from Content-Disposition header or use default
        let filename = `invoice_${id}.pdf`;
        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition) {
          const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1].replace(/['"]/g, '');
          }
        }
        
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'PDF downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download PDF');
        console.error('PDF download error:', err);
      }
    });
  }

  getTypeBadgeClass(type: string): string {
    if (type === 'ESTIMATE') return 'badge-estimate';
    if (type === 'GST') return 'badge-gst';
    return 'badge-info';
  }

  getStatusBadgeClass(status: string): string {
    if (status === 'RETURNED') return 'badge-returned';
    if (status === 'CORRECTED') return 'badge-warning';
    if (status === 'DRAFT') return 'badge-draft';
    return 'badge-success';
  }

  getPaymentBadgeClass(status: string): string {
    if (status === 'PAID') return 'badge-paid';
    if (status === 'PARTIAL') return 'badge-partial';
    return 'badge-pending';
  }

  getFormattedDate(billing: Billing): string {
    if (billing.invoiceDate) {
      const date = new Date(billing.invoiceDate);
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const year = date.getFullYear();
      return `${day}/${month}/${year}`;
    }
    return 'N/A';
  }

  hasActiveFilters(): boolean {
    return !!(this.selectedType || this.selectedParty || this.selectedStatus || (this.invoiceNumberFilter && this.invoiceNumberFilter.trim()));
  }

  clearFilters() {
    this.selectedType = '';
    this.selectedParty = '';
    this.selectedStatus = '';
    this.invoiceNumberFilter = '';
    this.applyFilters();
  }
}

