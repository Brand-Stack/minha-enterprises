import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AnimateOnScrollDirective } from './directives/animate-on-scroll.directive';
import { HoverScaleDirective } from './directives/hover-scale.directive';
import { RippleEffectDirective } from './directives/ripple-effect.directive';
import { HasPermissionDirective } from './directives/has-permission.directive';
import { DisableIfNoPermissionDirective } from './directives/disable-if-no-permission.directive';
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { ToastComponent } from './components/toast/toast.component';
import { ThemeToggleComponent } from './components/theme-toggle/theme-toggle.component';
import { AccessDeniedComponent } from '../modules/shared/access-denied/access-denied.component';
import { LastUpdatedByFieldComponent } from './components/last-updated-by-field/last-updated-by-field.component';
import { AutoLogoutDialogComponent } from './components/auto-logout-dialog/auto-logout-dialog.component';
import { EmployeeSelectorComponent } from './components/employee-selector/employee-selector.component';
import { AttendanceSummaryModalComponent } from './components/attendance-summary-modal/attendance-summary-modal.component';
import { AttendanceCalendarComponent } from './components/attendance-calendar/attendance-calendar.component';

@NgModule({
  declarations: [
    AnimateOnScrollDirective,
    HoverScaleDirective,
    RippleEffectDirective,
    HasPermissionDirective,
    DisableIfNoPermissionDirective,
    LoadingSpinnerComponent,
    ToastComponent,
    ThemeToggleComponent,
    AccessDeniedComponent,
    LastUpdatedByFieldComponent,
    AutoLogoutDialogComponent,
    EmployeeSelectorComponent,
    AttendanceSummaryModalComponent,
    AttendanceCalendarComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ],
  exports: [
    AnimateOnScrollDirective,
    HoverScaleDirective,
    RippleEffectDirective,
    HasPermissionDirective,
    DisableIfNoPermissionDirective,
    LoadingSpinnerComponent,
    ToastComponent,
    ThemeToggleComponent,
    AccessDeniedComponent,
    LastUpdatedByFieldComponent,
    AutoLogoutDialogComponent,
    EmployeeSelectorComponent,
    AttendanceSummaryModalComponent,
    AttendanceCalendarComponent,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ]
})
export class SharedModule { }

