import { Component, OnInit } from '@angular/core';
import { ApiService, PageResponse } from '../../../../core/services/api.service';

@Component({
  selector: 'app-invoice-list',
  template: `
    <div class="p-6">
      <div class="flex justify-between items-center mb-4">
        <h1 class="text-2xl font-bold">Invoices</h1>
        <!--<button mat-raised-button color="primary">Create Transaction</button> -->
      </div>
      <p>Invoice list component - Implement invoice creation and listing here</p>
    </div>
  `
})
export class InvoiceListComponent implements OnInit {
  constructor(private apiService: ApiService) {}
  ngOnInit() {}
}

