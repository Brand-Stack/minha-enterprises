import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ExcelExportService } from '../../../../core/services/excel-export.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

export interface Party {
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
  selector: 'app-party-list',
  template: `
    <div class="p-6">
      <div class="flex justify-between items-center mb-4">
        <h1 class="text-2xl font-bold">Clients</h1>
        <div class="flex gap-2">
          <button *appHasPermission="'CLIENTS:export'" mat-raised-button color="accent" (click)="exportToExcel()">
            <mat-icon>download</mat-icon>
            Export to Excel
          </button>
          <button *appHasPermission="'CLIENTS:create'" mat-raised-button color="primary" (click)="openForm()">Add Client</button>
        </div>
      </div>
      
      <div class="mb-4">
        <input type="text" placeholder="Search..." 
               [(ngModel)]="searchTerm" 
               (keyup.enter)="search()"
               class="w-full px-4 py-2 border rounded">
      </div>

      <table mat-table [dataSource]="parties" class="w-full">
        <ng-container matColumnDef="partyCode">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let party">{{ party.partyCode }}</td>
        </ng-container>

        <ng-container matColumnDef="partyName">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let party">{{ party.partyName }}</td>
        </ng-container>

        <ng-container matColumnDef="contactPerson">
          <th mat-header-cell *matHeaderCellDef>Contact</th>
          <td mat-cell *matCellDef="let party">{{ party.contactPerson }}</td>
        </ng-container>

        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let party">{{ party.phone }}</td>
        </ng-container>

        <ng-container matColumnDef="lastUpdatedBy">
          <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
          <td mat-cell *matCellDef="let party">{{ party.lastUpdatedBy || '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let party">
            <button mat-icon-button (click)="edit(party)" [appDisableIfNoPermission]="'CLIENTS:edit'">
              <mat-icon>edit</mat-icon>
            </button>
            <button *appHasPermission="'CLIENTS:delete'" mat-icon-button (click)="delete(party.id)">
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>

      <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
                     [pageSizeOptions]="[5, 10, 25, 100]"
                     showFirstLastButtons (page)="onPageChange($event)"></mat-paginator>
    </div>
  `
})
export class PartyListComponent implements OnInit {
  parties: Party[] = [];
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

  ngOnInit() {
    this.loadParties();
  }

  loadParties() {
    this.apiService.getPaged<Party>('/clients', this.page, this.pageSize)
      .subscribe({
        next: (response) => {
          this.parties = response?.content ?? [];
          this.totalElements = response?.totalElements ?? 0;
        },
        error: () => {
          this.parties = [];
          this.totalElements = 0;
        }
      });
  }

  search() {
    if (this.searchTerm && this.searchTerm.trim()) {
      this.page = 0;
      this.apiService.search<Party>('/clients', this.searchTerm.trim(), this.page, this.pageSize)
        .subscribe({
          next: (response) => {
            this.parties = response?.content ?? [];
            this.totalElements = response?.totalElements ?? 0;
          },
          error: () => {
            this.parties = [];
            this.totalElements = 0;
          }
        });
    } else {
      this.loadParties();
    }
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.searchTerm && this.searchTerm.trim()) {
      this.search();
    } else {
      this.loadParties();
    }
  }

  openForm() {
    this.router.navigate(['/clients/create']);
  }

  edit(party: Party) {
    this.router.navigate(['/clients/edit', party.id]);
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this party? This action cannot be undone.')) {
      this.apiService.delete('/clients', id).subscribe(() => {
        this.loadParties();
      });
    }
  }

  exportToExcel() {
    // Load all parties for export
    this.apiService.get<PageResponse<Party>>('/clients', { page: 0, size: 10000 }).subscribe({
      next: async (response) => {
        if (response.content.length === 0) {
          this.toastService.warning('Warning', 'No data to export');
          return;
        }
        
        const headers = ['Party Code', 'Party Name', 'Contact Person', 'Phone', 'WhatsApp No', 'Email', 'GSTIN', 'Address', 'City', 'State', 'Pincode', 'Party Type'];
        const exportData = response.content.map(party => ({
          'Party Code': party.partyCode || '',
          'Party Name': party.partyName || '',
          'Contact Person': party.contactPerson || '',
          'Phone': party.phone || '',
          'WhatsApp No': (party as any).whatsappNumber || '',
          'Email': party.email || '',
          'GSTIN': party.gstin || '',
          'Address': party.address || '',
          'City': party.city || '',
          'State': party.state || '',
          'Pincode': party.pincode || '',
          'Party Type': party.partyType || ''
        }));
        
        try {
          await this.excelExportService.exportToExcel(exportData, 'Parties', headers);
          this.toastService.success('Success', 'Excel file downloaded successfully');
        } catch (error) {
          this.toastService.error('Error', 'Failed to generate Excel file');
        }
      },
      error: () => {
        this.toastService.error('Error', 'Failed to export parties');
      }
    });
  }
}

