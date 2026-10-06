import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { DatePipe } from '@angular/common';
import { OnboardQuotationService } from '../../../../core/services/onboard-quotation.service';
import { OnboardQuotation } from '../../../../core/models/onboard-quotation.model';
import { filenameFromContentDisposition } from '../../../../core/utils/content-disposition-filename.util';

@Component({
  selector: 'app-onboard-quotation-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Onboard Rate Quotations</h1>
          <p class="body-small text-neutral-light">Manage custom rate quotations for prospective onboard customers</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" (keyup.enter)="applyFilters()" placeholder="Quotation no, customer, branch…">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-field-status">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="statusFilter" (selectionChange)="applyFilters()">
              <mat-option value="">All</mat-option>
              <mat-option value="DRAFT">Draft</mat-option>
              <mat-option value="APPROVED">Approved</mat-option>
              <mat-option value="ACTIVE">Active</mat-option>
              <mat-option value="EXPIRED">Expired</mat-option>
            </mat-select>
          </mat-form-field>

          <button mat-raised-button color="primary" (click)="applyFilters()" matTooltip="Apply search and filters">
            <mat-icon>search</mat-icon> Search
          </button>
          <button *appHasPermission="'ONBOARD_QUOTATION:create'" mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon> <span>Create Onboard Quotation</span>
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
              <th mat-header-cell *matHeaderCellDef>Prospective Customer</th>
              <td mat-cell *matCellDef="let q">{{ q.customerName }}</td>
            </ng-container>

            <ng-container matColumnDef="branchName">
              <th mat-header-cell *matHeaderCellDef>Branch</th>
              <td mat-cell *matCellDef="let q">{{ q.branchName || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="slabs">
              <th mat-header-cell *matHeaderCellDef>Configured Slabs</th>
              <td mat-cell *matCellDef="let q">
                <span class="slab-count-badge">
                  {{ getSelectedSlabsCount(q) }} / {{ q.slabs?.length || 0 }} selected
                </span>
              </td>
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
                <span [class]="'badge badge-' + (q.status || 'DRAFT').toLowerCase()">{{ q.status || 'DRAFT' }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="text-right">Actions</th>
              <td mat-cell *matCellDef="let q" class="text-right">
                <button *appHasPermission="'ONBOARD_QUOTATION:edit'" mat-icon-button color="primary" (click)="edit(q.id)" matTooltip="Edit Quotation">
                  <mat-icon>edit</mat-icon>
                </button>
                <button *appHasPermission="'ONBOARD_QUOTATION:print'" mat-icon-button (click)="printPdf(q)" matTooltip="Print Quotation">
                  <mat-icon>print</mat-icon>
                </button>
                <button *appHasPermission="'ONBOARD_QUOTATION:download'" mat-icon-button (click)="downloadPdf(q)" matTooltip="Download PDF">
                  <mat-icon>picture_as_pdf</mat-icon>
                </button>
                <button *appHasPermission="'ONBOARD_QUOTATION:export'" mat-icon-button (click)="downloadExcel(q)" matTooltip="Export to Excel">
                  <mat-icon>table_view</mat-icon>
                </button>
                <button *appHasPermission="'ONBOARD_QUOTATION:delete'" mat-icon-button color="warn" (click)="delete(q)" matTooltip="Delete">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>

          <div *ngIf="dataSource.data.length === 0" class="empty-state p-6 text-center text-neutral-light">
            No onboard quotations found. Click "Create Onboard Quotation" to get started.
          </div>

          <mat-paginator [length]="totalElements"
                         [pageSize]="pageSize"
                         [pageIndex]="pageIndex"
                         [pageSizeOptions]="[5, 10, 25, 50]"
                         (page)="onPageChange($event)"
                         showFirstLastButtons>
          </mat-paginator>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .search-field { width: 240px; margin-bottom: -1.25em; }
    .filter-field-status { width: 130px; margin-bottom: -1.25em; }
    .header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 16px; }
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .data-table { width: 100%; }
    .slab-count-badge {
      background: #eff6ff;
      color: #1d4ed8;
      font-size: 11px;
      font-weight: 600;
      padding: 3px 8px;
      border-radius: 6px;
      border: 1px solid #bfdbfe;
    }
  `]
})
export class OnboardQuotationListComponent implements OnInit {
  displayedColumns: string[] = ['quotationNumber', 'customerName', 'branchName', 'slabs', 'effectiveDate', 'validTillDate', 'status', 'actions'];
  dataSource = new MatTableDataSource<OnboardQuotation>([]);
  loading = false;

  searchTerm = '';
  statusFilter = '';

  pageIndex = 0;
  pageSize = 10;
  totalElements = 0;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private onboardService: OnboardQuotationService,
    private router: Router,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadQuotations();
  }

  loadQuotations(): void {
    this.loading = true;
    this.onboardService.findAll(
      this.pageIndex,
      this.pageSize,
      'effectiveDate',
      'desc',
      undefined,
      undefined,
      this.statusFilter || undefined,
      this.searchTerm || undefined
    ).subscribe({
      next: (res) => {
        this.dataSource.data = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.loading = false;
      },
      error: (err) => {
        this.toast.error('Error', err?.error?.message || 'Failed to load onboard quotations');
        this.loading = false;
      }
    });
  }

  getSelectedSlabsCount(q: OnboardQuotation): number {
    if (!q.slabs) return 0;
    return q.slabs.filter(s => s.selected !== false).length;
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.loadQuotations();
  }

  onPageChange(event: any): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadQuotations();
  }

  createNew(): void {
    this.router.navigate(['/onboard-quotations/new']);
  }

  edit(id: string): void {
    this.router.navigate(['/onboard-quotations/edit', id]);
  }

  printPdf(q: OnboardQuotation): void {
    this.onboardService.printPdf(q.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: () => this.toast.error('Error', 'Failed to print PDF')
    });
  }

  downloadPdf(q: OnboardQuotation): void {
    this.onboardService.downloadPdf(q.id!).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `Onboard-Quotation-${q.quotationNumber}.pdf`;
        a.click();
      },
      error: () => this.toast.error('Error', 'Failed to download PDF')
    });
  }

  downloadExcel(q: OnboardQuotation): void {
    this.onboardService.exportExcel(q.id!).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `Onboard-Quotation-${q.quotationNumber}.xlsx`;
        a.click();
      },
      error: () => this.toast.error('Error', 'Failed to download Excel')
    });
  }

  delete(q: OnboardQuotation): void {
    if (confirm(`Are you sure you want to delete onboard quotation ${q.quotationNumber}?`)) {
      this.onboardService.delete(q.id!).subscribe({
        next: () => {
          this.toast.success('Success', 'Onboard quotation deleted');
          this.loadQuotations();
        },
        error: (err) => this.toast.error('Error', err?.error?.message || 'Failed to delete quotation')
      });
    }
  }
}
