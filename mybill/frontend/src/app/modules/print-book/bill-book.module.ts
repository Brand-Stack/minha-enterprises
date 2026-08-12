import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { LayoutComponent } from '../dashboard/layout/layout.component';
import { BillBookListComponent } from './bill-book-list/bill-book-list.component';

@NgModule({
  declarations: [BillBookListComponent],
  imports: [
    CommonModule,
    RouterModule.forChild([
      {
        path: '',
        component: LayoutComponent,
        children: [
          { path: '', component: BillBookListComponent }
        ]
      }
    ]),
    MatTableModule,
    MatButtonModule
  ]
})
export class BillBookModule { }

