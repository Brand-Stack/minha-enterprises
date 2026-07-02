import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
    LastUpdatedByFieldComponent
  ],
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule
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
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule
  ]
})
export class SharedModule { }

