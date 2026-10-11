import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule } from '@angular/router';

import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ZoneConfigurationRoutingModule } from './zone-configuration-routing.module';
import { ZoneConfigurationListComponent } from './zone-configuration-list/zone-configuration-list.component';
import { ZoneConfigurationFormComponent } from './zone-configuration-form/zone-configuration-form.component';
import { SharedModule } from '../../../shared/shared.module';

@NgModule({
    declarations: [
        ZoneConfigurationListComponent,
        ZoneConfigurationFormComponent
    ],
    imports: [
        CommonModule,
        ZoneConfigurationRoutingModule,
        FormsModule,
        ReactiveFormsModule,
        HttpClientModule,
        RouterModule,
        MatTableModule,
        MatPaginatorModule,
        MatSortModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        MatSelectModule,
        MatSnackBarModule,
        MatDividerModule,
        MatProgressSpinnerModule,
        SharedModule
    ]
})
export class ZoneConfigurationModule { }
