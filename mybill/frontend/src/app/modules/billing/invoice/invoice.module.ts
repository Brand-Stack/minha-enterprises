import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule } from '@angular/material/paginator';
import { InvoiceListComponent } from './invoice-list/invoice-list.component';

@NgModule({
  declarations: [InvoiceListComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule.forChild([
      { path: '', component: InvoiceListComponent }
    ]),
    MatTableModule,
    MatButtonModule,
    MatPaginatorModule
  ]
})
export class InvoiceModule { }

