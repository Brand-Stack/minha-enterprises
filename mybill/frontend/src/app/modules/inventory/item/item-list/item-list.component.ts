import { Component, OnInit } from '@angular/core';
import { ApiService, PageResponse } from '../../../../core/services/api.service';

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
}

@Component({
  selector: 'app-item-list',
  template: `
    <div class="p-6">
      <div class="flex justify-between items-center mb-4">
        <h1 class="text-2xl font-bold">Items</h1>
        <button mat-raised-button color="primary">Add Item</button>
      </div>
      <div class="mb-4">
        <input type="text" placeholder="Search..." [(ngModel)]="searchTerm" 
               (keyup.enter)="search()" class="w-full px-4 py-2 border rounded">
      </div>
      <table mat-table [dataSource]="items" class="w-full">
        <ng-container matColumnDef="itemCode"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let item">{{ item.itemCode }}</td></ng-container>
        <ng-container matColumnDef="itemName"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let item">{{ item.itemName }}</td></ng-container>
        <ng-container matColumnDef="stockQuantity"><th mat-header-cell *matHeaderCellDef>Stock</th><td mat-cell *matCellDef="let item">{{ item.stockQuantity }}</td></ng-container>
        <ng-container matColumnDef="sellingPrice"><th mat-header-cell *matHeaderCellDef>Price</th><td mat-cell *matCellDef="let item">{{ item.sellingPrice }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th><td mat-cell *matCellDef="let item"><button mat-icon-button><mat-icon>edit</mat-icon></button><button mat-icon-button><mat-icon>delete</mat-icon></button></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize" (page)="onPageChange($event)"></mat-paginator>
    </div>
  `
})
export class ItemListComponent implements OnInit {
  items: Item[] = [];
  displayedColumns = ['itemCode', 'itemName', 'stockQuantity', 'sellingPrice', 'actions'];
  searchTerm = '';
  page = 0;
  pageSize = 10;
  totalElements = 0;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadItems();
  }

  loadItems() {
    this.apiService.getPaged<Item>('/items', this.page, this.pageSize).subscribe(response => {
      this.items = response.content;
      this.totalElements = response.totalElements;
    });
  }

  search() {
    if (this.searchTerm) {
      this.apiService.search<Item>('/items', this.searchTerm, this.page, this.pageSize).subscribe(response => {
        this.items = response.content;
        this.totalElements = response.totalElements;
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
}

