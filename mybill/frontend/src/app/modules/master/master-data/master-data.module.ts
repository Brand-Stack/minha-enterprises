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
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { SharedModule } from '../../../shared/shared.module';
import { MasterDataListComponent } from './master-data-list/master-data-list.component';
import { MasterDataFormComponent } from './master-data-form/master-data-form.component';

@NgModule({
  declarations: [MasterDataListComponent, MasterDataFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild([
      { path: '', component: MasterDataListComponent },
      { path: 'create', component: MasterDataFormComponent },
      { path: 'edit/:id', component: MasterDataFormComponent }
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
    MatTabsModule,
    SharedModule
  ]
})
export class MasterDataModule { }

