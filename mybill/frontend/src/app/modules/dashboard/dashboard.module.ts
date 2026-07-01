import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SharedModule } from '../../shared/shared.module';
import { DashboardComponent } from './dashboard.component';
import { LayoutComponent } from './layout/layout.component';
import { AwbSearchPanelComponent } from './awb-search-panel/awb-search-panel.component';

@NgModule({
  declarations: [DashboardComponent, LayoutComponent],
  imports: [
    AwbSearchPanelComponent,
    CommonModule,
    FormsModule,
    RouterModule.forChild([
      {
        path: '',
        component: LayoutComponent,
        children: [
          { path: '', component: DashboardComponent }
        ]
      }
    ]),
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatProgressSpinnerModule,
    SharedModule
  ]
})
export class DashboardModule { }

