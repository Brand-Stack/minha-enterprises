import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatMenuModule } from '@angular/material/menu';
import { MatCardModule } from '@angular/material/card';

import { SharedModule } from '../../shared/shared.module';
import { AttendanceRoutingModule } from './attendance-routing.module';

import { AttendanceDashboardComponent } from './attendance-dashboard/attendance-dashboard.component';
import { MyAttendanceComponent } from './my-attendance/my-attendance.component';
import { AttendanceListComponent } from './attendance-list/attendance-list.component';
import { LeaveManagementComponent } from './leave-management/leave-management.component';
import { PermissionManagementComponent } from './permission-management/permission-management.component';
import { PayrollManagementComponent } from './payroll-management/payroll-management.component';
import { DeviceIntegrationComponent } from './device-integration/device-integration.component';
import { AttendanceSettingsComponent } from './attendance-settings/attendance-settings.component';

@NgModule({
  declarations: [
    AttendanceDashboardComponent,
    MyAttendanceComponent,
    AttendanceListComponent,
    LeaveManagementComponent,
    PermissionManagementComponent,
    PayrollManagementComponent,
    DeviceIntegrationComponent,
    AttendanceSettingsComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    AttendanceRoutingModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatIconModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatMenuModule,
    MatCardModule,
    SharedModule
  ]
})
export class AttendanceModule { }
