import { Component, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router, ActivatedRoute } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

export interface MasterData {
  id: string;
  name: string;
  description: string;
  type: 'ITEM_CATEGORY' | 'ITEM_UNIT' | 'EMPLOYEE_CATEGORY' | 'EXPENSE_CATEGORY';
  active: boolean;
  role: string;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-master-data-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Create Category</h1>
          <p class="body-small text-neutral-light">Manage reusable values for Categories and Units</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" 
                   (keyup.enter)="search()" 
                   placeholder="Search...">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Add Category</span>
          </button>
        </div>
      </div>

      <mat-card class="table-card">
        <mat-tab-group [(selectedIndex)]="selectedTabIndex" (selectedIndexChange)="onTabChange($event)">
          <mat-tab label="Item Categories">
            <ng-template matTabContent>
              <div *ngIf="loading" class="loading-state">
                <app-loading-spinner></app-loading-spinner>
              </div>
              <div *ngIf="!loading">
                <table mat-table [dataSource]="dataSource" class="data-table">
                  <ng-container matColumnDef="name">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let item">{{ item.name }}</td>
                  </ng-container>
                  <ng-container matColumnDef="description">
                    <th mat-header-cell *matHeaderCellDef>Description</th>
                    <td mat-cell *matCellDef="let item">{{ item.description || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="role">
                    <th mat-header-cell *matHeaderCellDef>Role</th>
                    <td mat-cell *matCellDef="let item">{{ item.role || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="active">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let item">
                      <span [class]="item.active ? 'badge-status badge-success' : 'badge-status badge-danger'">
                        {{ item.active ? 'Active' : 'Inactive' }}
                      </span>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="lastUpdatedBy">
                    <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
                    <td mat-cell *matCellDef="let item">{{ item.lastUpdatedBy || '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef>Actions</th>
                    <td mat-cell *matCellDef="let item">
                      <button mat-icon-button (click)="edit(item.id)" matTooltip="Edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button mat-icon-button (click)="delete(item.id)" matTooltip="Delete" color="warn">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="getDisplayedColumns()"></tr>
                  <tr mat-row *matRowDef="let row; columns: getDisplayedColumns()"></tr>
                </table>
                <mat-paginator [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons></mat-paginator>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Item Units">
            <ng-template matTabContent>
              <div *ngIf="loading" class="loading-state">
                <app-loading-spinner></app-loading-spinner>
              </div>
              <div *ngIf="!loading">
                <table mat-table [dataSource]="dataSource" class="data-table">
                  <ng-container matColumnDef="name">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let item">{{ item.name }}</td>
                  </ng-container>
                  <ng-container matColumnDef="description">
                    <th mat-header-cell *matHeaderCellDef>Description</th>
                    <td mat-cell *matCellDef="let item">{{ item.description || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="active">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let item">
                      <span [class]="item.active ? 'badge-status badge-success' : 'badge-status badge-danger'">
                        {{ item.active ? 'Active' : 'Inactive' }}
                      </span>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="lastUpdatedBy">
                    <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
                    <td mat-cell *matCellDef="let item">{{ item.lastUpdatedBy || '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef>Actions</th>
                    <td mat-cell *matCellDef="let item">
                      <button mat-icon-button (click)="edit(item.id)" matTooltip="Edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button mat-icon-button (click)="delete(item.id)" matTooltip="Delete" color="warn">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="getDisplayedColumns()"></tr>
                  <tr mat-row *matRowDef="let row; columns: getDisplayedColumns()"></tr>
                </table>
                <mat-paginator [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons></mat-paginator>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Employee Categories">
            <ng-template matTabContent>
              <div *ngIf="loading" class="loading-state">
                <app-loading-spinner></app-loading-spinner>
              </div>
              <div *ngIf="!loading">
                <table mat-table [dataSource]="dataSource" class="data-table">
                  <ng-container matColumnDef="name">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let item">{{ item.name }}</td>
                  </ng-container>
                  <ng-container matColumnDef="description">
                    <th mat-header-cell *matHeaderCellDef>Description</th>
                    <td mat-cell *matCellDef="let item">{{ item.description || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="role">
                    <th mat-header-cell *matHeaderCellDef>Role</th>
                    <td mat-cell *matCellDef="let item">{{ item.role || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="active">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let item">
                      <span [class]="item.active ? 'badge-status badge-success' : 'badge-status badge-danger'">
                        {{ item.active ? 'Active' : 'Inactive' }}
                      </span>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="lastUpdatedBy">
                    <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
                    <td mat-cell *matCellDef="let item">{{ item.lastUpdatedBy || '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef>Actions</th>
                    <td mat-cell *matCellDef="let item">
                      <button mat-icon-button (click)="edit(item.id)" matTooltip="Edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button mat-icon-button (click)="delete(item.id)" matTooltip="Delete" color="warn">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="getDisplayedColumns()"></tr>
                  <tr mat-row *matRowDef="let row; columns: getDisplayedColumns()"></tr>
                </table>
                <mat-paginator [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons></mat-paginator>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Expense Categories">
            <ng-template matTabContent>
              <div *ngIf="loading" class="loading-state">
                <app-loading-spinner></app-loading-spinner>
              </div>
              <div *ngIf="!loading">
                <table mat-table [dataSource]="dataSource" class="data-table">
                  <ng-container matColumnDef="name">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let item">{{ item.name }}</td>
                  </ng-container>
                  <ng-container matColumnDef="description">
                    <th mat-header-cell *matHeaderCellDef>Description</th>
                    <td mat-cell *matCellDef="let item">{{ item.description || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="role">
                    <th mat-header-cell *matHeaderCellDef>Role</th>
                    <td mat-cell *matCellDef="let item">{{ item.role || 'N/A' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="active">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let item">
                      <span [class]="item.active ? 'badge-status badge-success' : 'badge-status badge-danger'">
                        {{ item.active ? 'Active' : 'Inactive' }}
                      </span>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="lastUpdatedBy">
                    <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
                    <td mat-cell *matCellDef="let item">{{ item.lastUpdatedBy || '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef>Actions</th>
                    <td mat-cell *matCellDef="let item">
                      <button mat-icon-button (click)="edit(item.id)" matTooltip="Edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button mat-icon-button (click)="delete(item.id)" matTooltip="Delete" color="warn">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="getDisplayedColumns()"></tr>
                  <tr mat-row *matRowDef="let row; columns: getDisplayedColumns()"></tr>
                </table>
                <mat-paginator [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons></mat-paginator>
              </div>
            </ng-template>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-actions { display: flex; gap: 16px; align-items: center; }
    .search-field { width: 300px; }
    .table-card { padding: 0; }
    .data-table { width: 100%; }
    .loading-state { padding: 40px; text-align: center; }
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
    .badge-danger {
      background: #FEE2E2;
      color: #991B1B;
    }
  `]
})
export class MasterDataListComponent implements OnInit, AfterViewInit {
  displayedColumns: string[] = ['name', 'description', 'role', 'active', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<MasterData>([]);
  loading = false;
  searchTerm = '';
  selectedTabIndex = 0;
  currentType: 'ITEM_CATEGORY' | 'ITEM_UNIT' | 'EMPLOYEE_CATEGORY' | 'EXPENSE_CATEGORY' = 'ITEM_CATEGORY';
  
  getDisplayedColumns(): string[] {
    if (this.currentType === 'EMPLOYEE_CATEGORY') {
      return ['role', 'description', 'lastUpdatedBy', 'actions'];
    }
    return ['name', 'description', 'active', 'lastUpdatedBy', 'actions'];
  }

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService
  ) {}

  private static readonly CATEGORY_TYPES: MasterData['type'][] = [
    'ITEM_CATEGORY', 'ITEM_UNIT', 'EMPLOYEE_CATEGORY', 'EXPENSE_CATEGORY'
  ];

  private applyTypeFromQuery(type: string | undefined): void {
    if (!type) {
      return;
    }
    const index = MasterDataListComponent.CATEGORY_TYPES.indexOf(type as MasterData['type']);
    if (index >= 0) {
      this.selectedTabIndex = index;
      this.currentType = MasterDataListComponent.CATEGORY_TYPES[index];
      this.displayedColumns = this.getDisplayedColumns();
    }
  }

  ngOnInit() {
    this.applyTypeFromQuery(this.route.snapshot.queryParams['type']);
    this.displayedColumns = this.getDisplayedColumns();
    this.loadData();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
  }

  onTabChange(index: number) {
    this.currentType = MasterDataListComponent.CATEGORY_TYPES[index];
    this.displayedColumns = this.getDisplayedColumns();
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.apiService.get<MasterData[]>(`/master-data/type/${this.currentType}`).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Failed to load categories');
        this.loading = false;
      }
    });
  }

  search() {
    if (!this.searchTerm.trim()) {
      this.loadData();
      return;
    }
    this.loading = true;
    this.apiService.get<PageResponse<MasterData>>(`/master-data/search`, {
      searchTerm: this.searchTerm,
      type: this.currentType,
      page: 0,
      size: 10
    }).subscribe({
      next: (response) => {
        this.dataSource.data = response.content;
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Search failed');
        this.loading = false;
      }
    });
  }

  createNew() {
    this.router.navigate(['/master/master-data/create'], { queryParams: { type: this.currentType } });
  }

  edit(id: string) {
    this.router.navigate(['/master/master-data/edit', id], { queryParams: { type: this.currentType } });
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this category?')) {
      this.apiService.delete('/master-data', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Category deleted successfully');
          this.loadData();
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to delete category');
        }
      });
    }
  }
}

