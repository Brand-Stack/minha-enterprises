import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatRadioModule } from '@angular/material/radio';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatMenuModule } from '@angular/material/menu';
import { SharedModule } from '../../shared/shared.module';
import { PurchaseBillsListComponent } from './purchase-bills/purchase-bills-list/purchase-bills-list.component';
import { PurchaseBillsFormComponent } from './purchase-bills/purchase-bills-form/purchase-bills-form.component';
import { PaymentOutListComponent } from './payment-out/payment-out-list/payment-out-list.component';
import { PaymentOutFormComponent } from './payment-out/payment-out-form/payment-out-form.component';
import { ExpensesListComponent } from './expenses/expenses-list/expenses-list.component';
import { ExpensesFormComponent } from './expenses/expenses-form/expenses-form.component';

@NgModule({
  declarations: [
    PurchaseBillsListComponent,
    PurchaseBillsFormComponent,
    PaymentOutListComponent,
    PaymentOutFormComponent,
    ExpensesListComponent,
    ExpensesFormComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      { path: '', redirectTo: 'purchase-bills', pathMatch: 'full' },
      { path: 'purchase-bills', component: PurchaseBillsListComponent },
      { path: 'purchase-bills/create', component: PurchaseBillsFormComponent },
      { path: 'purchase-bills/edit/:id', component: PurchaseBillsFormComponent },
      { path: 'purchase-bills/view/:id', component: PurchaseBillsFormComponent },
      { path: 'payment-out', component: PaymentOutListComponent },
      { path: 'payment-out/create', component: PaymentOutFormComponent },
      { path: 'payment-out/edit/:id', component: PaymentOutFormComponent },
      { path: 'cash-in', component: ExpensesListComponent },
      { path: 'cash-in/create', component: ExpensesFormComponent },
      { path: 'cash-in/edit/:id', component: ExpensesFormComponent }
    ]),
    MatTableModule,
    MatSortModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatRadioModule,
    MatAutocompleteModule,
    MatCheckboxModule,
    MatMenuModule,
    SharedModule
  ]
})
export class PurchaseExpenseModule { }

