import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

interface Party {
  id: string;
  partyName: string;
  phone?: string;
}

@Component({
  selector: 'app-payment-out-form',
  template: `
    <div class="page-container">
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-4xl mx-auto">
        <div class="card-header">
          <h2>Payment-Out</h2>
          <div class="header-icons">
            <button mat-icon-button>
              <mat-icon>calculate</mat-icon>
            </button>
            <button mat-icon-button (click)="cancel()" class="close-btn">
              <mat-icon>close</mat-icon>
            </button>
          </div>
        </div>

        <mat-card-content>
          <form [formGroup]="paymentForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="form-grid">
              <!-- Left Column -->
              <div class="form-column">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Search by Name/Phone *</mat-label>
                  <input matInput formControlName="partySearch"
                         [matAutocomplete]="partyAuto"
                         (input)="searchParty($event)"
                         placeholder="Search by Name/Phone *"
                         [disabled]="isView"
                         required>
                  <mat-autocomplete #partyAuto="matAutocomplete" (optionSelected)="onPartySelected($event)">
                    <mat-option *ngFor="let party of filteredParties" [value]="party">
                      <div class="autocomplete-option">
                        <span>{{ party.partyName }}</span>
                        <span class="text-sm text-gray-500" *ngIf="party.phone">{{ party.phone }}</span>
                      </div>
                    </mat-option>
                  </mat-autocomplete>
                  <mat-error *ngIf="paymentForm.get('partySearch')?.hasError('required')">Party is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Payment Type</mat-label>
                  <mat-select formControlName="paymentType" [disabled]="isView">
                    <mat-option value="CASH">Cash</mat-option>
                    <mat-option value="ONLINE">Online</mat-option>
                  </mat-select>
                  <a href="#" class="add-link">+ Add Payment type</a>
                </mat-form-field>

                <div class="description-section">
                  <button mat-raised-button type="button" (click)="showDescription = !showDescription" class="desc-btn">
                    <mat-icon>description</mat-icon>
                    ADD DESCRIPTION
                  </button>
                  <input type="file" #fileInput accept="image/*" (change)="onFileSelected($event)" class="hidden">
                  <button mat-icon-button type="button" (click)="fileInput.click()" class="camera-btn">
                    <mat-icon>camera_alt</mat-icon>
                  </button>
                  <textarea *ngIf="showDescription" matInput formControlName="description" 
                            placeholder="Enter description..." rows="3" class="desc-textarea"></textarea>
                </div>
              </div>

              <!-- Right Column -->
              <div class="form-column">
                <div class="form-row">
                  <mat-form-field appearance="outline" class="flex-1">
                    <mat-label>Receipt No</mat-label>
                    <input matInput formControlName="receiptNumber" [disabled]="true">
                  </mat-form-field>
                </div>

                <div class="form-row">
                  <mat-form-field appearance="outline" class="flex-1">
                    <mat-label>Date</mat-label>
                    <input matInput [matDatepicker]="picker" formControlName="date" required [disabled]="isView">
                    <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                    <mat-datepicker #picker></mat-datepicker>
                  </mat-form-field>
                </div>

                <div class="form-row">
                  <mat-form-field appearance="outline" class="flex-1">
                    <mat-label>Paid</mat-label>
                    <input matInput type="number" formControlName="paidAmount" 
                           step="0.01" min="0" required [disabled]="isView">
                    <mat-error *ngIf="paymentForm.get('paidAmount')?.hasError('required')">Amount is required</mat-error>
                  </mat-form-field>
                </div>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Reference No.</mat-label>
                  <input matInput formControlName="referenceNumber" [disabled]="isView">
                </mat-form-field>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex gap-4 pt-4">
              <button *ngIf="!isView" mat-raised-button color="primary" type="submit" 
                      [disabled]="paymentForm.invalid || loading"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">Save</span>
                <span *ngIf="loading">Processing...</span>
              </button>
              <button *ngIf="!isView" mat-raised-button color="accent" type="button" 
                      (click)="share()" 
                      [disabled]="paymentForm.invalid || loading"
                      class="btn-secondary">
                <mat-icon>share</mat-icon>
                <span>Share</span>
              </button>
              <button mat-button type="button" (click)="cancel()">{{ isView ? 'Back' : 'Cancel' }}</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; animation: fadeSlideUp 0.3s ease-out; }
    .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-icons { display: flex; gap: 8px; }
    .close-btn { color: #666; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    .form-column { display: flex; flex-direction: column; gap: 16px; }
    .form-row { display: flex; gap: 16px; }
    .description-section { border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; }
    .desc-btn { width: 100%; margin-bottom: 8px; }
    .camera-btn { margin-left: 8px; }
    .desc-textarea { width: 100%; margin-top: 8px; }
    .add-link { color: #5B6FE8; text-decoration: none; font-size: 12px; }
    .hidden { display: none; }
    .autocomplete-option { display: flex; flex-direction: column; }
    input, textarea { caret-color: auto !important; }
    @media (max-width: 768px) {
      .form-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class PaymentOutFormComponent implements OnInit {
  paymentForm!: FormGroup;
  loading = false;
  paymentId: string | null = null;
  isView = false;
  showDescription = false;
  
  availableParties: Party[] = [];
  filteredParties: Party[] = [];
  partySearch$ = new Subject<string>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private toastService: ToastService
  ) {
    this.paymentForm = this.fb.group({
      partyId: ['', Validators.required],
      partySearch: ['', Validators.required],
      paymentType: ['CASH'],
      date: [new Date(), Validators.required],
      receiptNumber: [''],
      paidAmount: [0, [Validators.required, Validators.min(0.01)]],
      description: [''],
      referenceNumber: ['']
    });
  }

  ngOnInit() {
    this.loadParties();
    
    this.route.params.subscribe(params => {
      this.paymentId = params['id'] || null;
      this.isView = this.route.snapshot.url.some(segment => segment.path === 'view');
      
      if (this.paymentId) {
        this.loadPayment(this.paymentId);
      }
    });

    this.partySearch$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(searchTerm => {
      this.filterParties(searchTerm);
    });
  }

  loadParties() {
    this.apiService.get<any>('/clients', { page: 0, size: 1000 }).subscribe({
      next: (response) => {
        this.availableParties = response.content || response;
        this.filteredParties = this.availableParties;
      }
    });
  }

  searchParty(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.partySearch$.next(value);
  }

  filterParties(searchTerm: string) {
    if (!searchTerm) {
      this.filteredParties = this.availableParties;
      return;
    }
    const term = searchTerm.toLowerCase();
    this.filteredParties = this.availableParties.filter(p => 
      p.partyName.toLowerCase().includes(term) || 
      (p.phone && p.phone.includes(term))
    );
  }

  onPartySelected(event: any) {
    const party: Party = event.option.value;
    this.paymentForm.patchValue({
      partyId: party.id,
      partySearch: party.partyName
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      // TODO: Upload file
      this.toastService.info('Info', 'File upload functionality coming soon');
    }
  }

  loadPayment(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/payment-out/${id}`).subscribe({
      next: (payment) => {
        this.loading = false;
        const party = this.availableParties.find(p => p.id === payment.partyId);
        this.paymentForm.patchValue({
          partyId: payment.partyId,
          partySearch: payment.partyName || (party?.partyName || ''),
          paymentType: payment.paymentType || 'CASH',
          date: payment.date ? new Date(payment.date) : null,
          receiptNumber: payment.receiptNumber ?? '',
          paidAmount: payment.paidAmount != null ? Number(payment.paidAmount) : 0,
          description: payment.description ?? '',
          referenceNumber: payment.referenceNumber ?? ''
        });
        if (this.isView) {
          this.paymentForm.disable();
        }
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load payment-out record');
      }
    });
  }

  onSubmit() {
    if (this.paymentForm.valid) {
      this.loading = true;
      const formValue = this.paymentForm.value;
      
      const paymentData = {
        partyId: formValue.partyId,
        paymentType: formValue.paymentType,
        date: formValue.date,
        paidAmount: formValue.paidAmount,
        description: formValue.description,
        referenceNumber: formValue.referenceNumber
      };

      const request = this.paymentId
        ? this.apiService.put<any>('/payment-out', this.paymentId, paymentData)
        : this.apiService.post<any>('/payment-out', paymentData);

      request.subscribe({
        next: () => {
          this.loading = false;
          this.toastService.success('Success', `Payment-out ${this.paymentId ? 'updated' : 'created'} successfully`);
          setTimeout(() => {
            this.router.navigate(['/purchase-expense/payment-out']);
          }, 500);
        },
        error: () => {
          this.loading = false;
          this.toastService.error('Error', `Failed to ${this.paymentId ? 'update' : 'create'} payment-out`);
        }
      });
    }
  }

  share() {
    // TODO: Implement share functionality
    this.toastService.info('Info', 'Share functionality coming soon');
  }

  cancel() {
    this.router.navigate(['/purchase-expense/payment-out']);
  }
}

