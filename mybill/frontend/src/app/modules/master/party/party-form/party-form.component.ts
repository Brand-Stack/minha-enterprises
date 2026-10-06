import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-party-form',
  template: `
    <div class="party-form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Edit' : 'Create' }} Party</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="partyForm" (ngSubmit)="onSubmit()">
            <div class="form-row">
              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Party Code</mat-label>
                <input matInput formControlName="partyCode" [disabled]="true" [readonly]="true">
                <mat-hint>Auto-generated based on party type (CU001 for Customer, SU001 for Supplier)</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Party Name *</mat-label>
                <input matInput formControlName="partyName" required>
                <mat-error *ngIf="partyForm.get('partyName')?.hasError('required')">Party name is required</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Party Type *</mat-label>
                <mat-select formControlName="partyType" required>
                  <mat-option value="CUSTOMER">Customer</mat-option>
                  <mat-option value="SUPPLIER">Supplier</mat-option>
                </mat-select>
                <mat-error *ngIf="partyForm.get('partyType')?.hasError('required')">Party type is required</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Contact Person</mat-label>
                <input matInput formControlName="contactPerson">
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Phone</mat-label>
                <input matInput formControlName="phone" type="tel" (input)="onPhoneChange()" pattern="[0-9]*">
                <mat-error *ngIf="partyForm.get('phone')?.hasError('pattern')">Phone number should contain only digits</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="form-field">
                <mat-label>WhatsApp No</mat-label>
                <input matInput formControlName="whatsappNumber" type="tel" [readonly]="partyForm.get('sameAsPhone')?.value" [disabled]="partyForm.get('sameAsPhone')?.value" pattern="[0-9]*">
                <mat-error *ngIf="partyForm.get('whatsappNumber')?.hasError('pattern')">WhatsApp number should contain only digits</mat-error>
              </mat-form-field>
              
              <div class="checkbox-container">
                <mat-checkbox formControlName="sameAsPhone" (change)="onSameAsPhoneChange()">
                  Same as Phone Number
                </mat-checkbox>
              </div>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="form-field">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="text" placeholder="user@example.com, another@example.com">
                <mat-error *ngIf="partyForm.get('email')?.hasError('multipleEmails')">Invalid email format in list</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field">
                <mat-label>GSTIN</mat-label>
                <input matInput formControlName="gstin" maxlength="15">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Address</mat-label>
              <textarea matInput formControlName="address" rows="2"></textarea>
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline" class="form-field">
                <mat-label>City</mat-label>
                <input matInput formControlName="city">
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field">
                <mat-label>State</mat-label>
                <input matInput formControlName="state">
              </mat-form-field>

              <mat-form-field appearance="outline" class="form-field-sm">
                <mat-label>Pincode</mat-label>
                <input matInput formControlName="pincode" maxlength="6">
              </mat-form-field>
            </div>

            <app-last-updated-by-field *ngIf="isEdit" [value]="lastUpdatedBy"></app-last-updated-by-field>

            <div class="form-actions">
              <button mat-button type="button" (click)="cancel()">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="partyForm.invalid || saving">
                <mat-spinner *ngIf="saving" diameter="20"></mat-spinner>
                <span *ngIf="!saving">{{ isEdit ? 'Update' : 'Create' }}</span>
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .party-form-container {
      padding: 24px;
      width: 100%;
    }

    .form-row {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }

    .form-field {
      flex: 1;
      min-width: 200px;
    }

    .form-field-sm {
      flex: 0.5;
      min-width: 120px;
    }

    .full-width {
      width: 100%;
      margin-bottom: 16px;
    }

    .form-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 24px;
    }

    .checkbox-container {
      display: flex;
      align-items: center;
      padding-top: 8px;
      min-width: 200px;
    }

    .checkbox-container mat-checkbox {
      font-size: 14px;
    }

    @media (max-width: 768px) {
      .form-row {
        flex-direction: column;
      }

      .form-field,
      .form-field-sm {
        min-width: 100%;
      }
    }
  `]
})
export class PartyFormComponent implements OnInit {
  partyForm: FormGroup;
  isEdit = false;
  lastUpdatedBy = '';
  partyId: string | null = null;
  saving = false;
  returnUrl: string | null = null;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService
  ) {
    this.partyForm = this.fb.group({
      partyCode: [''],
      partyName: ['', Validators.required],
      partyType: ['CUSTOMER', Validators.required],
      contactPerson: [''],
      email: ['', [this.multipleEmailsValidator]],
      phone: ['', [Validators.pattern(/^[0-9]*$/)]],
      sameAsPhone: [false],
      whatsappNumber: ['', [Validators.pattern(/^[0-9]*$/)]],
      address: [''],
      city: [''],
      state: [''],
      pincode: [''],
      gstin: ['']
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['name']) {
        this.partyForm.patchValue({ partyName: params['name'] });
      }
      if (params['returnUrl']) {
        this.returnUrl = params['returnUrl'];
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.partyId = id;
      this.loadParty(id);
    }
  }

  loadParty(id: string) {
    this.apiService.get<any>(`/clients/${id}`).subscribe({
      next: (party) => {
        this.lastUpdatedBy = party.lastUpdatedBy ?? '';
        const sameAsPhone = party.whatsappNumber && party.phone && party.whatsappNumber === party.phone;
        this.partyForm.patchValue({
          partyCode: party.partyCode || '',
          partyName: party.partyName,
          partyType: party.partyType,
          contactPerson: party.contactPerson,
          email: party.email,
          phone: party.phone,
          sameAsPhone: sameAsPhone,
          whatsappNumber: party.whatsappNumber || '',
          address: party.address,
          city: party.city,
          state: party.state,
          pincode: party.pincode,
          gstin: party.gstin
        });
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load party');
        this.router.navigate(['/clients']);
      }
    });
  }

  onPhoneChange() {
    // If "Same as Phone Number" is checked, update WhatsApp number when phone changes
    if (this.partyForm.get('sameAsPhone')?.value) {
      const phoneValue = this.partyForm.get('phone')?.value || '';
      this.partyForm.patchValue({
        whatsappNumber: phoneValue
      });
    }
  }

  onSameAsPhoneChange() {
    const sameAsPhone = this.partyForm.get('sameAsPhone')?.value;
    if (sameAsPhone) {
      // When checked: Copy phone number to WhatsApp and disable WhatsApp field
      const phoneValue = this.partyForm.get('phone')?.value || '';
      this.partyForm.patchValue({
        whatsappNumber: phoneValue
      });
    } else {
      // When unchecked: Clear WhatsApp number so user can enter manually
      // (Optional: You can remove this line if you want to keep the existing value)
      // this.partyForm.patchValue({ whatsappNumber: '' });
    }
  }


  onSubmit() {
    if (this.partyForm.valid) {
      this.saving = true;
      const formValue = { ...this.partyForm.value };
      
      // Remove sameAsPhone from data (it's just a UI helper)
      const { sameAsPhone, ...partyData } = formValue;
      
      // Always omit partyCode - backend will auto-generate it based on party type
      // For new parties, backend generates it
      // For updates, backend preserves the existing code
      delete partyData.partyCode;

      if (this.isEdit && this.partyId) {
        // Include partyCode for updates (backend will preserve it)
        this.apiService.put('/clients', this.partyId, partyData).subscribe({
          next: () => {
            this.saving = false;
            this.toastService.success('Success', 'Party updated successfully');
            this.router.navigate(['/clients']);
          },
          error: (err) => {
            this.saving = false;
            let message = 'Failed to update party';
            
            // Handle different error response structures
            if (err.error) {
              if (err.error.message) {
                message = err.error.message;
              } else if (typeof err.error === 'string') {
                message = err.error;
              }
            }
            
            // Special handling for 409 Conflict (Resource Already Exists)
            if (err.status === 409) {
              this.toastService.error('Duplicate Entry', message);
            } else {
              this.toastService.error('Error', message);
            }
          }
        });
      } else {
        this.apiService.post('/clients', partyData).subscribe({
          next: (createdParty) => {
            this.saving = false;
            this.toastService.success('Success', 'Party created successfully');
            
            if (this.returnUrl) {
              this.router.navigateByUrl(this.returnUrl, { 
                state: { selectedParty: createdParty } 
              });
            } else {
              this.router.navigate(['/clients']);
            }
          },
          error: (err) => {
            this.saving = false;
            let message = 'Failed to create party';
            
            // Handle different error response structures
            if (err.error) {
              if (err.error.message) {
                message = err.error.message;
              } else if (typeof err.error === 'string') {
                message = err.error;
              }
            }
            
            // Special handling for 409 Conflict (Resource Already Exists)
            if (err.status === 409) {
              this.toastService.error('Duplicate Entry', message);
            } else {
              this.toastService.error('Error', message);
            }
          }
        });
      }
    }
  }

  cancel() {
    if (this.returnUrl) {
      this.router.navigateByUrl(this.returnUrl);
    } else {
      this.router.navigate(['/clients']);
    }
  }

  // Custom validator for multiple comma-separated emails
  multipleEmailsValidator(control: any) {
    if (!control.value) {
      return null;
    }
    const emails = control.value.split(',').map((e: string) => e.trim()).filter((e: string) => e.length > 0);
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    
    for (const email of emails) {
      if (!emailRegex.test(email)) {
        return { multipleEmails: true };
      }
    }
    return null;
  }
}
