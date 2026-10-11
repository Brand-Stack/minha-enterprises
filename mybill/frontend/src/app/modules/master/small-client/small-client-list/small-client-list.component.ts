import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ExcelExportService } from '../../../../core/services/excel-export.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import {
  clearListFilterState,
  loadListFilterState,
  saveListFilterState
} from '../../../../core/utils/list-filter-state.util';

const SMALL_CLIENT_LIST_FILTER_KEY = 'small_client_list_filters';

export interface SmallClientRow {
  id: string;
  partyCode: string;
  partyName: string;
  contactPerson: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  state: string;
  pincode: string;
  gstin: string;
  partyType: string;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-small-client-list',
  template: `
    <div class="p-6">
      <div class="flex justify-between items-center mb-4">
        <h1 class="text-2xl font-bold">Small Clients</h1>
        <div class="flex gap-2">
          <button *appHasPermission="'SMALL_CLIENTS:export'" mat-raised-button color="accent" (click)="exportToExcel()">
            <mat-icon>download</mat-icon>
            Export to Excel
          </button>
          <button *appHasPermission="'SMALL_CLIENTS:create'" mat-raised-button color="primary" (click)="openForm()">Add Small Client</button>
        </div>
      </div>
      <div class="mb-4">
        <input type="text" placeholder="Search..." [(ngModel)]="searchTerm" (keyup.enter)="search()"
               class="w-full px-4 py-2 border rounded">
      </div>
      <table mat-table [dataSource]="rows" class="w-full">
        <ng-container matColumnDef="partyCode">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.partyCode }}</td>
        </ng-container>
        <ng-container matColumnDef="partyName">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let row">{{ row.partyName }}</td>
        </ng-container>
        <ng-container matColumnDef="contactPerson">
          <th mat-header-cell *matHeaderCellDef>Contact</th>
          <td mat-cell *matCellDef="let row">{{ row.contactPerson }}</td>
        </ng-container>
        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let row">{{ row.phone }}</td>
        </ng-container>
        <ng-container matColumnDef="lastUpdatedBy">
          <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
          <td mat-cell *matCellDef="let row">{{ row.lastUpdatedBy || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            <button mat-icon-button (click)="edit(row)" [appDisableIfNoPermission]="'SMALL_CLIENTS:edit'"><mat-icon>edit</mat-icon></button>
            <button *appHasPermission="'SMALL_CLIENTS:delete'" mat-icon-button (click)="delete(row.id)"><mat-icon>delete</mat-icon></button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
                     [pageSizeOptions]="[5, 10, 25, 100]" showFirstLastButtons (page)="onPageChange($event)"></mat-paginator>
    </div>
  `
})
export class SmallClientListComponent implements OnInit {
  rows: SmallClientRow[] = [];
  displayedColumns = ['partyCode', 'partyName', 'contactPerson', 'phone', 'lastUpdatedBy', 'actions'];
  searchTerm = '';
  page = 0;
  pageSize = 10;
  totalElements = 0;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private excelExportService: ExcelExportService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.restoreFilters();
    this.reloadList();
  }

  private persistFilters(): void {
    saveListFilterState(SMALL_CLIENT_LIST_FILTER_KEY, {
      searchTerm: this.searchTerm,
      page: this.page,
      pageSize: this.pageSize
    });
  }

  private restoreFilters(): void {
    const saved = loadListFilterState<Record<string, unknown>>(SMALL_CLIENT_LIST_FILTER_KEY);
    if (!saved) return;
    if (saved['searchTerm'] != null) this.searchTerm = String(saved['searchTerm']);
    if (typeof saved['page'] === 'number') this.page = saved['page'];
    if (typeof saved['pageSize'] === 'number') this.pageSize = saved['pageSize'];
  }

  private reloadList(): void {
    this.persistFilters();
    if (this.searchTerm?.trim()) {
      this.search(false);
    } else {
      this.load();
    }
  }

  load(): void {
    this.persistFilters();
    this.apiService.getPaged<SmallClientRow>('/small-clients', this.page, this.pageSize).subscribe({
      next: (response) => {
        this.rows = response?.content ?? [];
        this.totalElements = response?.totalElements ?? 0;
      },
      error: () => {
        this.rows = [];
        this.totalElements = 0;
      }
    });
  }

  search(resetPage = true): void {
    if (resetPage) {
      this.page = 0;
    }
    this.persistFilters();
    if (this.searchTerm?.trim()) {
      this.apiService.search<SmallClientRow>('/small-clients', this.searchTerm.trim(), this.page, this.pageSize).subscribe({
        next: (response) => {
          this.rows = response?.content ?? [];
          this.totalElements = response?.totalElements ?? 0;
        },
        error: () => {
          this.rows = [];
          this.totalElements = 0;
        }
      });
    } else {
      this.load();
    }
  }

  onPageChange(event: any): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.searchTerm?.trim()) {
      this.search(false);
    } else {
      this.load();
    }
  }

  openForm(): void {
    this.persistFilters();
    this.router.navigate(['/small-clients/create']);
  }

  edit(row: SmallClientRow): void {
    this.persistFilters();
    this.router.navigate(['/small-clients/edit', row.id]);
  }

  delete(id: string): void {
    if (confirm('Delete this small client?')) {
      this.apiService.delete('/small-clients', id).subscribe(() => this.load());
    }
  }

  exportToExcel(): void {
    this.apiService.get<PageResponse<SmallClientRow>>('/small-clients', { page: 0, size: 10000 }).subscribe({
      next: async (response) => {
        if (!response.content.length) {
          this.toastService.warning('Warning', 'No data to export');
          return;
        }
        const headers = ['Code', 'Name', 'Contact', 'Phone', 'Email', 'GSTIN', 'Address', 'City', 'State', 'Pincode'];
        const exportData = response.content.map((p) => ({
          Code: p.partyCode || '',
          Name: p.partyName || '',
          Contact: p.contactPerson || '',
          Phone: p.phone || '',
          Email: p.email || '',
          GSTIN: p.gstin || '',
          Address: p.address || '',
          City: p.city || '',
          State: p.state || '',
          Pincode: p.pincode || ''
        }));
        await this.excelExportService.exportToExcel(exportData, 'SmallClients', headers);
        this.toastService.success('Success', 'Excel file downloaded');
      },
      error: () => this.toastService.error('Error', 'Export failed')
    });
  }
}
