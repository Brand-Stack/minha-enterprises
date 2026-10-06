import { NgModule } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
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
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SharedModule } from '../../../shared/shared.module';

import { MonthlyCourierQuotationListComponent, PdfConfirmDialogComponent } from './monthly-courier-quotation-list/monthly-courier-quotation-list.component';
import { MonthlyCourierQuotationFormComponent } from './monthly-courier-quotation-form/monthly-courier-quotation-form.component';
import { CollectionCenterListComponent } from './collection-center-list/collection-center-list.component';
import { CollectionCenterFormComponent } from './collection-center-form/collection-center-form.component';

@NgModule({
  declarations: [
    MonthlyCourierQuotationListComponent,
    MonthlyCourierQuotationFormComponent,
    CollectionCenterListComponent,
    CollectionCenterFormComponent
  ],
  providers: [DatePipe],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      { path: 'collection-center', component: CollectionCenterListComponent },
      { path: 'collection-center/create', component: CollectionCenterFormComponent },
      { path: 'collection-center/edit/:id', component: CollectionCenterFormComponent },
      { path: '', component: MonthlyCourierQuotationListComponent },
      { path: 'create', component: MonthlyCourierQuotationFormComponent },
      { path: 'edit/:id', component: MonthlyCourierQuotationFormComponent }
    ]),
    MatTableModule,
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
    MatAutocompleteModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    PdfConfirmDialogComponent,
    SharedModule
  ]
})
export class MonthlyCourierQuotationModule { }
