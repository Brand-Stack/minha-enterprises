import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { SharedModule } from '../../shared/shared.module';
import { LayoutComponent } from '../dashboard/layout/layout.component';
import { ReportsComponent } from './reports.component';
import { BillingReportComponent } from './billing-report/billing-report.component';
import { StockReportComponent } from './stock-report/stock-report.component';
// SalesReportComponent removed - module deprecated
import { PartyReportComponent } from './party-report/party-report.component';
import { PurchaseReportComponent } from './purchase-report/purchase-report.component';
import { ExpenseReportComponent } from './expense-report/expense-report.component';
import { CashInHandReportComponent } from './cash-in-hand-report/cash-in-hand-report.component';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatPaginatorModule } from '@angular/material/paginator';
import { ReactiveFormsModule } from '@angular/forms';
import { CourierReportComponent } from './courier-report/courier-report.component';
import { CustomerCollectionReportComponent } from './customer-collection-report/customer-collection-report.component';
import { CashBookingReportComponent } from './cash-booking-report/cash-booking-report.component';
import { SmallClientEntryReportComponent } from './small-client-entry-report/small-client-entry-report.component';
import { GstReportComponent } from './gst-report/gst-report.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { PdfConfirmDialogComponent } from '../master/monthly-courier-quotation/monthly-courier-quotation-list/monthly-courier-quotation-list.component';
import { PermissionGuard } from '../../core/guards/permission.guard';
import { MatSortModule } from '@angular/material/sort';

@NgModule({
  declarations: [
    ReportsComponent,
    BillingReportComponent,
    StockReportComponent,
    // SalesReportComponent removed - module deprecated
    PartyReportComponent,
    PurchaseReportComponent,
    ExpenseReportComponent,
    CashInHandReportComponent,
    CourierReportComponent,
    CustomerCollectionReportComponent,
    CashBookingReportComponent,
    SmallClientEntryReportComponent,
    GstReportComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatPaginatorModule,
    RouterModule.forChild([
      {
        path: '',
        component: LayoutComponent,
        children: [
          { path: '', component: ReportsComponent },
          { 
            path: 'gst-report', 
            component: GstReportComponent,
            canActivate: [PermissionGuard],
            data: { permission: { module: 'GST_REPORT', action: 'view' } }
          }
        ]
      }
    ]),
    MatTableModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatSelectModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatTooltipModule,
    MatDialogModule,
    PdfConfirmDialogComponent,
    SharedModule
  ]
})
export class ReportsModule { }

