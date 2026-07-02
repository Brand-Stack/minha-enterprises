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
import { SmallClientEntryListComponent, PdfConfirmDialogComponent } from './small-client-entry-list/small-client-entry-list.component';
import { SmallClientEntryFormComponent } from './small-client-entry-form/small-client-entry-form.component';
import { LayoutComponent } from '../../dashboard/layout/layout.component';

@NgModule({
  declarations: [SmallClientEntryListComponent, SmallClientEntryFormComponent],
  providers: [DatePipe],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      {
        path: '',
        component: LayoutComponent,
        children: [
          { path: '', component: SmallClientEntryListComponent },
          { path: 'create', component: SmallClientEntryFormComponent },
          { path: 'edit/:id', component: SmallClientEntryFormComponent }
        ]
      }
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
export class SmallClientEntryModule {}
