import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PermissionGuard } from '../../core/guards/permission.guard';
import { AttendanceDashboardComponent } from './attendance-dashboard/attendance-dashboard.component';
import { MyAttendanceComponent } from './my-attendance/my-attendance.component';
import { AttendanceListComponent } from './attendance-list/attendance-list.component';
import { LeaveManagementComponent } from './leave-management/leave-management.component';
import { PermissionManagementComponent } from './permission-management/permission-management.component';
import { PayrollManagementComponent } from './payroll-management/payroll-management.component';
import { DeviceIntegrationComponent } from './device-integration/device-integration.component';
import { AttendanceSettingsComponent } from './attendance-settings/attendance-settings.component';

const routes: Routes = [
  { path: '', redirectTo: 'my-attendance', pathMatch: 'full' },
  { path: 'dashboard', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'my-attendance',
    component: MyAttendanceComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'MY_ATTENDANCE', action: 'view' } }
  },
  {
    path: 'daily-list',
    component: AttendanceListComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'MASTER_ATTENDANCE', action: 'view' } }
  },
  {
    path: 'leaves',
    component: LeaveManagementComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'LEAVES', action: 'view' } }
  },
  {
    path: 'permissions',
    component: PermissionManagementComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'PERMISSIONS', action: 'view' } }
  },
  {
    path: 'payroll',
    component: PayrollManagementComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'PAYROLL_PAYSLIPS', action: 'view' } }
  },
  {
    path: 'payslips',
    component: PayrollManagementComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'PAYROLL_PAYSLIPS', action: 'view' }, tab: 'MY_PAYSLIPS' }
  },
  {
    path: 'salary-advances',
    component: PayrollManagementComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'PAYROLL_PAYSLIPS', action: 'view' }, tab: 'SALARY_ADVANCES' }
  },
  {
    path: 'devices',
    component: DeviceIntegrationComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'BIOMETRIC_DEVICES', action: 'view' } }
  },
  {
    path: 'settings',
    component: AttendanceSettingsComponent,
    canActivate: [PermissionGuard],
    data: { permission: { module: 'WORKING_HOURS_CONFIG', action: 'view' } }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AttendanceRoutingModule { }
