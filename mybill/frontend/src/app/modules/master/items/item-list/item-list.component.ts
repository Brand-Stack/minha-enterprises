import { Component, OnInit, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { AnimationService } from '../../../../core/services/animation.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ExcelExportService } from '../../../../core/services/excel-export.service';

export interface Item {
  id: string;
  itemCode: string;
  itemName: string;
  description: string;
  category: string;
  unit: string;
  purchasePrice: number;
  sellingPrice: number;
  stockQuantity: number;
  minStockLevel: number;
  hsnCode: string;
  taxRate: number;
  customFields?: { [key: string]: any }; // Custom fields support
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-item-list',
  template: `
    <div class="page-container" [@fadeSlideUp]>
      <!-- Page Header -->
      <div class="page-header">
        <div>
          <h1 class="h2">Items</h1>
          <p class="body-small text-neutral-light">Manage inventory items and stock</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" 
                   (keyup.enter)="search()" 
                   placeholder="Search by code, name, category...">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Add Item</span>
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
            <!-- Code Column -->
            <ng-container matColumnDef="itemCode">
              <th mat-header-cell *matHeaderCellDef class="table-header">Code</th>
              <td mat-cell *matCellDef="let item" class="table-cell mono">{{ item.itemCode }}</td>
            </ng-container>

            <!-- Name Column -->
            <ng-container matColumnDef="itemName">
              <th mat-header-cell *matHeaderCellDef class="table-header">Name</th>
              <td mat-cell *matCellDef="let item" class="table-cell">{{ item.itemName }}</td>
            </ng-container>

            <!-- Category Column -->
            <ng-container matColumnDef="category">
              <th mat-header-cell *matHeaderCellDef class="table-header">Category</th>
              <td mat-cell *matCellDef="let item" class="table-cell">
                <span class="badge-status badge-info">{{ item.category || 'N/A' }}</span>
              </td>
            </ng-container>

            <!-- Stock Column -->
            <ng-container matColumnDef="stockQuantity">
              <th mat-header-cell *matHeaderCellDef class="table-header">Stock</th>
              <td mat-cell *matCellDef="let item" class="table-cell">
                <span [class]="getStockClass(item.stockQuantity, item.minStockLevel)">
                  {{ item.stockQuantity || 0 }} {{ item.unit || '' }}
                </span>
              </td>
            </ng-container>

            <!-- Price Column -->
            <ng-container matColumnDef="sellingPrice">
              <th mat-header-cell *matHeaderCellDef class="table-header">Price</th>
              <td mat-cell *matCellDef="let item" class="table-cell">
                ₹{{ item.sellingPrice || 0 }}
              </td>
            </ng-container>

            <!-- Last Updated By Column -->
            <ng-container matColumnDef="lastUpdatedBy">
              <th mat-header-cell *matHeaderCellDef class="table-header">Last Updated By</th>
              <td mat-cell *matCellDef="let item" class="table-cell">{{ item.lastUpdatedBy || '—' }}</td>
            </ng-container>

            <!-- Actions Column -->
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="table-header">Actions</th>
              <td mat-cell *matCellDef="let item" class="table-cell">
                <div class="action-buttons">
                  <button mat-icon-button (click)="edit(item.id)" 
                          class="btn-icon"
                          matTooltip="Edit">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button (click)="delete(item.id)" 
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
            <mat-icon>inventory_2</mat-icon>
          </div>
          <h3 class="empty-title">No Items Found</h3>
          <p class="empty-description">Get started by creating your first item</p>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Create Item</span>
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

    .search-field {
      width: 300px;
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

    .badge-info {
      background: #DBEAFE;
      color: #1E40AF;
    }

    /* Stock Status Colors */
    .stock-low {
      color: #EF4444;
      font-weight: 600;
    }

    .stock-ok {
      color: #10B981;
    }

    .stock-warning {
      color: #F59E0B;
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
export class ItemListComponent implements OnInit, AfterViewInit {
  displayedColumns = ['itemCode', 'itemName', 'category', 'stockQuantity', 'sellingPrice', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<Item>([]);
  totalElements = 0;
  page = 0;
  pageSize = 10;
  searchTerm = '';
  loading = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('pageContainer') pageContainer!: ElementRef;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private animationService: AnimationService,
    private toastService: ToastService,
    private excelExportService: ExcelExportService
  ) {}

  ngOnInit() {
    this.loadItems();
  }

  ngAfterViewInit() {
    if (this.pageContainer) {
      this.animationService.fadeIn(this.pageContainer.nativeElement, 0.3);
    }
  }

  loadItems() {
    this.loading = true;
    this.apiService.getPaged<Item>('/items', this.page, this.pageSize)
      .subscribe({
        next: (response) => {
          this.dataSource.data = response.content;
          this.totalElements = response.totalElements;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.toastService.error('Error', 'Failed to load items');
        }
      });
  }

  search() {
    this.loading = true;
    if (this.searchTerm) {
      this.apiService.get<PageResponse<Item>>(`/items/search?searchTerm=${this.searchTerm}&page=${this.page}&size=${this.pageSize}`)
        .subscribe({
          next: (response) => {
            this.dataSource.data = response.content;
            this.totalElements = response.totalElements;
            this.loading = false;
          },
          error: () => {
            this.loading = false;
            this.toastService.error('Error', 'Search failed');
          }
        });
    } else {
      this.loadItems();
    }
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.searchTerm?.trim()) {
      this.search();
    } else {
      this.loadItems();
    }
  }

  createNew() {
    this.router.navigate(['/master/items/create']);
  }

  edit(id: string) {
    this.router.navigate(['/master/items/edit', id]);
  }

  delete(id: string) {
    if (confirm('Are you sure you want to permanently delete this item? This action cannot be undone.')) {
      this.apiService.delete('/items', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Item deleted successfully');
          this.loadItems();
        },
        error: () => {
          this.toastService.error('Error', 'Failed to delete item');
        }
      });
    }
  }

  getStockClass(stock: number, minStock: number): string {
    if (!stock || stock === 0) return 'stock-low';
    if (minStock && stock < minStock) return 'stock-warning';
    return 'stock-ok';
  }

  exportToExcel() {
    // Load all items for export
    this.apiService.get<PageResponse<Item>>('/items', { page: 0, size: 10000 }).subscribe({
      next: async (response) => {
        if (response.content.length === 0) {
          this.toastService.warning('Warning', 'No data to export');
          return;
        }
        
        const headers = ['Item Code', 'Item Name', 'Category', 'Unit', 'HSN Code', 'Tax Rate (%)', 'Purchase Price', 'Selling Price', 'Stock Quantity', 'Min Stock Level', 'Description'];
        const exportData = response.content.map(item => ({
          'Item Code': item.itemCode || '',
          'Item Name': item.itemName || '',
          'Category': item.category || '',
          'Unit': item.unit || '',
          'HSN Code': item.hsnCode || '',
          'Tax Rate (%)': item.taxRate || 0,
          'Purchase Price': item.purchasePrice || 0,
          'Selling Price': item.sellingPrice || 0,
          'Stock Quantity': item.stockQuantity || 0,
          'Min Stock Level': item.minStockLevel || 0,
          'Description': item.description || ''
        }));
        
        try {
          await this.excelExportService.exportToExcel(exportData, 'Items', headers);
          this.toastService.success('Success', 'Excel file downloaded successfully');
        } catch (error) {
          this.toastService.error('Error', 'Failed to generate Excel file');
        }
      },
      error: () => {
        this.toastService.error('Error', 'Failed to export items');
      }
    });
  }
}

