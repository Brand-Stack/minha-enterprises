import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule } from '@angular/material/dialog';
import { SharedModule } from '../../../shared/shared.module';
import { SmallClientListComponent } from './small-client-list/small-client-list.component';
import { SmallClientFormComponent } from './small-client-form/small-client-form.component';
import { LayoutComponent } from '../../dashboard/layout/layout.component';

@NgModule({
  declarations: [SmallClientListComponent, SmallClientFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      {
        path: '',
        component: LayoutComponent,
        children: [
          { path: '', component: SmallClientListComponent },
          { path: 'create', component: SmallClientFormComponent },
          { path: 'edit/:id', component: SmallClientFormComponent }
        ]
      }
    ]),
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatIconModule,
    MatSelectModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatDialogModule,
    SharedModule
  ]
})
export class SmallClientModule {}
