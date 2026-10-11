import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { SharedModule } from '../../shared/shared.module';
import { ChangePasswordComponent } from './change-password/change-password.component';

@NgModule({
  declarations: [ChangePasswordComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule.forChild([
      { path: 'change-password', component: ChangePasswordComponent },
      { path: '', redirectTo: 'change-password', pathMatch: 'full' }
    ]),
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    SharedModule
  ]
})
export class ProfileModule { }
