import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { SharedModule } from '../../../shared/shared.module';
import { OnboardQuotationListComponent } from './onboard-quotation-list/onboard-quotation-list.component';
import { OnboardQuotationFormComponent } from './onboard-quotation-form/onboard-quotation-form.component';

@NgModule({
  declarations: [
    OnboardQuotationListComponent,
    OnboardQuotationFormComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      { path: '', component: OnboardQuotationListComponent },
      { path: 'new', component: OnboardQuotationFormComponent },
      { path: 'edit/:id', component: OnboardQuotationFormComponent }
    ]),
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatCheckboxModule,
    SharedModule
  ]
})
export class OnboardQuotationModule { }
