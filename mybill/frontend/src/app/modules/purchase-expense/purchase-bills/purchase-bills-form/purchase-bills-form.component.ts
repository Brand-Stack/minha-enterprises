import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

interface Party {
  id: string;
  partyName: string;
  phone?: string;
}

interface Item {
  id: string;
  itemCode: string;
  itemName: string;
  enCode?: string;
  purchasePrice: number;
  taxRate: number;
  unit?: string;
}

@Component({
  selector: 'app-purchase-bills-form',
  template: `
    <div class="page-container">
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-6xl mx-auto">
        <div class="card-header">
          <h2>{{ isView ? 'View' : (isEdit ? 'Edit' : 'Create') }} Purchase Bill</h2>
          <button mat-icon-button (click)="cancel()" class="close-btn">
            <mat-icon>close</mat-icon>
          </button>
        </div>

        <mat-card-content>
          <form [formGroup]="purchaseForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Party & Bill Info Section -->
            <div class="form-section">
              <h3 class="section-title">Party & Bill Information</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Search by Name/Phone *</mat-label>
                  <input matInput formControlName="partySearch"
                         [matAutocomplete]="partyAuto"
                         (input)="searchParty($event)"
                         placeholder="Search party..."
                         [disabled]="isView"
                         required>
                  <mat-autocomplete #partyAuto="matAutocomplete" (optionSelected)="onPartySelected($event)">
                    <mat-option *ngFor="let party of filteredParties" [value]="party">
                      <div class="autocomplete-option">
                        <span>{{ party.partyName }}</span>
                        <span class="text-sm text-gray-500" *ngIf="party.phone">{{ party.phone }}</span>
                      </div>
                    </mat-option>
                    <mat-option *ngIf="showCreateParty && !isView" [value]="'__CREATE__'" class="create-option">
                      <mat-icon>add_circle</mat-icon>
                      <span>Create new supplier "{{ partySearchTerm }}"</span>
                    </mat-option>
                  </mat-autocomplete>
                  <mat-error *ngIf="purchaseForm.get('partySearch')?.hasError('required')">Party is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Phone No.</mat-label>
                  <input matInput formControlName="partyPhone" [disabled]="true">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Purchase Entry No</mat-label>
                  <input matInput formControlName="purchaseEntryNo" 
                         placeholder="Leave empty for auto-generation"
                         [disabled]="isView">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Purchase Invoice Number</mat-label>
                  <input matInput formControlName="billNumber" 
                         placeholder="Optional - Supplier's invoice number"
                         [disabled]="isView">
                  <mat-hint>Enter supplier's invoice number (optional)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Bill Date *</mat-label>
                  <input matInput [matDatepicker]="picker" formControlName="billDate" required [disabled]="isView">
                  <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                  <mat-datepicker #picker></mat-datepicker>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Payment Type *</mat-label>
                  <mat-select formControlName="paymentType" required [disabled]="isView">
                    <mat-option value="CASH">Cash</mat-option>
                    <mat-option value="ONLINE">Online</mat-option>
                    <mat-option value="CHEQUE">Cheque</mat-option>
                  </mat-select>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Payment Status *</mat-label>
                  <mat-select formControlName="paymentStatus" required [disabled]="isView" (selectionChange)="onPaymentStatusChange()">
                    <mat-option value="UNPAID">Unpaid</mat-option>
                    <mat-option value="PARTIAL">Partial</mat-option>
                    <mat-option value="PAID">Paid</mat-option>
                  </mat-select>
                  <mat-hint>If Paid, expense will be auto-created</mat-hint>
                </mat-form-field>

                <!-- Online Payment Method - Show when type is ONLINE -->
                <mat-form-field appearance="outline" class="w-full" *ngIf="purchaseForm.get('paymentType')?.value === 'ONLINE'">
                  <mat-label>Payment Method</mat-label>
                  <mat-select formControlName="onlinePaymentMethod" [disabled]="isView">
                    <mat-option value="GPAY">GPay</mat-option>
                    <mat-option value="PHONEPE">PhonePe</mat-option>
                    <mat-option value="PAYTM">Paytm</mat-option>
                    <mat-option value="OTHER_UPI">Other UPI</mat-option>
                    <mat-option value="OTHERS">Others</mat-option>
                  </mat-select>
                </mat-form-field>

                <!-- Online Payment Reference - Show when type is ONLINE -->
                <mat-form-field appearance="outline" class="w-full" *ngIf="purchaseForm.get('paymentType')?.value === 'ONLINE'">
                  <mat-label>UPI ID / Phone Number (Optional)</mat-label>
                  <input matInput formControlName="onlinePaymentReference" 
                         placeholder="Enter UPI ID or Phone Number"
                         [disabled]="isView">
                </mat-form-field>

                <!-- Partial Payment: Cash Amount + Online Amount (cash + online <= total) -->
                <ng-container *ngIf="purchaseForm.get('paymentStatus')?.value === 'PARTIAL'">
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Cash Amount</mat-label>
                    <input matInput type="number" formControlName="paidCashAmount" 
                           (change)="onPartialPaymentChange()" step="0.01" min="0" [disabled]="isView">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Online Amount</mat-label>
                    <input matInput type="number" formControlName="paidOnlineAmount" 
                           (change)="onPartialPaymentChange()" step="0.01" min="0" [disabled]="isView">
                  </mat-form-field>
                </ng-container>
                <mat-form-field appearance="outline" class="w-full" *ngIf="purchaseForm.get('paymentStatus')?.value === 'PARTIAL'">
                  <mat-label>Outstanding Amount</mat-label>
                  <input matInput formControlName="outstandingAmount" [disabled]="true">
                </mat-form-field>
              </div>
            </div>

            <!-- Items Table Section -->
            <div class="form-section">
              <h3 class="section-title">Items</h3>
              <div class="items-table-container">
                <table class="items-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>ITEM</th>
                      <th>CATEGORY</th>
                      <th>EN CODE</th>
                      <th>QTY</th>
                      <th>UNIT</th>
                      <th>PRICE</th>
                      <th>TAX %</th>
                      <th>AMOUNT</th>
                      <th *ngIf="!isView">Actions</th>
                    </tr>
                  </thead>
                  <tbody formArrayName="items">
                    <tr *ngFor="let item of itemsArray.controls; let i = index" [formGroupName]="i">
                      <td>{{ i + 1 }}</td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput formControlName="itemSearch"
                                 [matAutocomplete]="itemAuto"
                                 (input)="searchItem($event, i)"
                                 placeholder="Search item..."
                                 [disabled]="isView">
                          <mat-autocomplete #itemAuto="matAutocomplete" (optionSelected)="onItemSelected($event, i)">
                            <mat-option *ngFor="let item of filteredItems[i]" [value]="item">
                              {{ item.itemName }} ({{ item.itemCode }})
                            </mat-option>
                            <mat-option *ngIf="showCreateItemOption(i)" (click)="openCreateItemDialog(i); $event.stopPropagation()" class="create-item-option">
                              <mat-icon>add</mat-icon>
                              <span>Create Item "{{ getItemSearchValue(i) }}"</span>
                            </mat-option>
                          </mat-autocomplete>
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput formControlName="category" [readonly]="true" [disabled]="true" placeholder="Auto-filled">
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput formControlName="enCode" [readonly]="true" [disabled]="true" placeholder="Auto-filled">
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput type="number" formControlName="quantity" 
                                 (change)="calculateItemAmount(i)" 
                                 step="1" min="1" [disabled]="isView"
                                 (keydown)="preventDecimalInput($event)">
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput formControlName="unit" [readonly]="true" [disabled]="true" placeholder="Auto-filled">
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput type="number" formControlName="price" 
                                 (change)="calculateItemAmount(i)" 
                                 step="0.01" min="0" [disabled]="isView">
                        </mat-form-field>
                      </td>
                      <td>
                        <mat-form-field appearance="outline" class="item-field">
                          <input matInput type="number" formControlName="taxPercent" 
                                 (change)="calculateItemAmount(i)" 
                                 step="1" min="0" max="100" [disabled]="isView"
                                 (keydown)="preventDecimalInput($event)">
                        </mat-form-field>
                      </td>
                      <td>
                        <span class="amount-display">₹{{ getItemAmount(i) | number:'1.2-2' }}</span>
                      </td>
                      <td *ngIf="!isView">
                        <button mat-icon-button type="button" (click)="removeItem(i)" color="warn">
                          <mat-icon>delete</mat-icon>
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <button *ngIf="!isView" mat-raised-button type="button" (click)="addItem()" class="add-row-btn">
                  <mat-icon>add</mat-icon>
                  ADD ROW
                </button>
                <div class="totals-row">
                  <div class="total-label">ITEMS TOTAL</div>
                  <div class="total-qty">QTY: {{ getTotalQuantity() }}</div>
                  <div class="total-amount">AMOUNT: ₹{{ getTotalAmount() | number:'1.2-2' }}</div>
                </div>
                
                <!-- Partial Payment Breakdown - Show when PARTIAL status -->
                <div *ngIf="purchaseForm.get('paymentStatus')?.value === 'PARTIAL'" class="payment-breakdown mt-4 p-4 bg-gray-50 rounded-lg">
                  <div class="flex justify-between text-amber-600 font-medium mb-2">
                    <span>Cash Paid:</span>
                    <span class="font-mono">₹{{ (purchaseForm.get('paidCashAmount')?.value || 0) | number:'1.2-2' }}</span>
                  </div>
                  <div class="flex justify-between text-blue-600 font-medium mb-2">
                    <span>Online Paid:</span>
                    <span class="font-mono">₹{{ (purchaseForm.get('paidOnlineAmount')?.value || 0) | number:'1.2-2' }}</span>
                  </div>
                  <div class="flex justify-between text-green-600 font-medium mb-2">
                    <span>Total Paid:</span>
                    <span class="font-mono">₹{{ ((purchaseForm.get('paidCashAmount')?.value || 0) + (purchaseForm.get('paidOnlineAmount')?.value || 0)) | number:'1.2-2' }}</span>
                  </div>
                  <div class="flex justify-between text-orange-600 font-medium">
                    <span>Outstanding Amount:</span>
                    <span class="font-mono">₹{{ (purchaseForm.get('outstandingAmount')?.value || 0) | number:'1.2-2' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- File Upload Section -->
            <!-- Upload Purchase Bill Section -->
            <div class="form-section">
              <h3 class="section-title">Upload Purchase Bill</h3>
              <div *ngIf="!isView">
                <input type="file" #fileInput accept=".pdf,.jpg,.jpeg,.png,.doc,.docx" 
                       (change)="onFileSelected($event)" class="hidden">
                <button mat-raised-button type="button" (click)="fileInput.click()" class="upload-btn" [disabled]="uploadingFile">
                  <mat-icon>upload</mat-icon>
                  <span *ngIf="!uploadingFile">Upload Bill</span>
                  <span *ngIf="uploadingFile">Uploading...</span>
                </button>
                <span *ngIf="uploadedFileName" class="file-name">{{ uploadedFileName }}</span>
                <button *ngIf="uploadedFileName && !isView" mat-icon-button color="warn" (click)="removeFile()" type="button">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
              <div *ngIf="isView && uploadedBillFileUrl" class="uploaded-file-view">
                <div class="file-info">
                  <mat-icon>attach_file</mat-icon>
                  <span>Uploaded Bill Document</span>
                </div>
                <button mat-raised-button color="primary" (click)="downloadUploadedFile()">
                  <mat-icon>download</mat-icon>
                  Download File
                </button>
                <button mat-raised-button color="accent" (click)="viewUploadedFile()" *ngIf="isImageOrPdf()">
                  <mat-icon>visibility</mat-icon>
                  View File
                </button>
              </div>
              <div *ngIf="isView && !uploadedBillFileUrl" class="no-file">
                <span>No file uploaded</span>
              </div>
            </div>

            <!-- Notes Section -->
            <div class="form-section">
              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Notes</mat-label>
                <textarea matInput formControlName="notes" rows="3" [disabled]="isView"></textarea>
              </mat-form-field>
            </div>

            <!-- Actions -->
            <app-last-updated-by-field *ngIf="isEdit || isView" [value]="lastUpdatedBy"></app-last-updated-by-field>

            <div class="flex gap-4 pt-4">
              <button *ngIf="!isView" mat-raised-button color="primary" type="submit" 
                      [disabled]="purchaseForm.invalid || loading"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">{{ isEdit ? 'Update' : 'Save' }}</span>
                <span *ngIf="loading">Processing...</span>
              </button>
              <button *ngIf="!isView" mat-raised-button color="accent" type="button" 
                      (click)="saveAndPrint()" 
                      [disabled]="purchaseForm.invalid || loading"
                      class="btn-secondary">
                <mat-icon>print</mat-icon>
                <span>Save & Print</span>
              </button>
              <button *ngIf="isView && billId" mat-raised-button color="primary" type="button" 
                      (click)="editBill()" class="btn-primary">
                Edit Bill
              </button>
              <button *ngIf="billId" mat-raised-button color="accent" type="button" 
                      (click)="printBill()" class="btn-secondary">
                <mat-icon>print</mat-icon>
                <span>Print</span>
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
    .close-btn { color: #666; }
    .form-section { margin-bottom: 32px; padding: 24px; background: #F9FAFB; border-radius: 8px; }
    .section-title { font-size: 16px; font-weight: 600; color: #1A1D2E; margin-bottom: 16px; }
    .items-table-container { overflow-x: auto; }
    .items-table { width: 100%; border-collapse: collapse; background: white; }
    .items-table th { background: #E5E7EB; padding: 12px; text-align: left; font-weight: 600; font-size: 12px; }
    .items-table td { padding: 8px; border-bottom: 1px solid #E5E7EB; }
    .item-field { width: 100%; margin: 0; }
    .item-field ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    .amount-display { font-weight: 600; }
    .add-row-btn { margin-top: 16px; }
    .totals-row { display: flex; justify-content: flex-end; gap: 24px; margin-top: 16px; padding: 12px; background: #F3F4F6; font-weight: 600; }
    .hidden { display: none; }
    .upload-btn { margin-right: 12px; }
    .file-name { color: #059669; }
    .autocomplete-option { display: flex; flex-direction: column; }
    input, textarea { caret-color: auto !important; }
    input, textarea, mat-select { display: flex; align-items: center; }
    .uploaded-file-view {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: #F9FAFB;
      border-radius: 8px;
      margin-top: 12px;
    }
    .file-info {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
    }
    .no-file {
      padding: 12px;
      color: #6B7280;
      font-style: italic;
    }
    .file-name {
      margin-left: 12px;
      color: #1A1D2E;
    }
    .create-item-option {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #5B6FE8;
      font-weight: 500;
    }
    .create-item-option mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .create-option {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #2e7d32;
      font-weight: 500;
    }
    .create-option mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
  `]
})
export class PurchaseBillsFormComponent implements OnInit, AfterViewInit {
  purchaseForm!: FormGroup;
  loading = false;
  billId: string | null = null;
  isEdit = false;
  lastUpdatedBy = '';
  isView = false;
  
  availableParties: Party[] = [];
  filteredParties: Party[] = [];
  partySearch$ = new Subject<string>();
  showCreateParty = false;  // Show "Create Party" option in dropdown
  partySearchTerm = '';     // Current search term for party
  
  availableItems: Item[] = [];
  filteredItems: { [key: number]: Item[] } = {};
  
  uploadedFileName: string | null = null;
  uploadedFile: File | null = null;
  uploadingFile = false;
  uploadedBillFileUrl: string | null = null;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private toastService: ToastService,
    private http: HttpClient
  ) {
    this.purchaseForm = this.fb.group({
      partyId: ['', Validators.required],
      partySearch: ['', Validators.required],
      partyPhone: [''],
      purchaseEntryNo: [''], // Purchase Entry No (optional, auto-generated)
      billNumber: [''], // Purchase Invoice Number (optional - supplier's invoice number)
      billDate: [new Date(), Validators.required],
      paymentType: ['CASH', Validators.required],
      paymentStatus: ['UNPAID', Validators.required],
      onlinePaymentMethod: [''],
      onlinePaymentReference: [''],
      paidAmount: [0],
      paidCashAmount: [0],
      paidOnlineAmount: [0],
      outstandingAmount: [0],
      items: this.fb.array([], Validators.required),
      notes: [''],
      uploadedBillFile: ['']
    });
  }

  get itemsArray(): FormArray {
    return this.purchaseForm.get('items') as FormArray;
  }

  ngOnInit() {
    this.loadParties();
    this.loadItems();
    
    this.route.params.subscribe(params => {
      this.billId = params['id'] || null;
      this.isEdit = this.route.snapshot.url.some(segment => segment.path === 'edit');
      this.isView = this.route.snapshot.url.some(segment => segment.path === 'view');
      
      if (this.billId) {
        this.loadBill(this.billId);
      } else {
        this.addItem();
      }
    });

    // Handle return from Items create page
    this.route.queryParams.subscribe(params => {
      if (params['itemId'] && params['itemIndex']) {
        const itemId = params['itemId'];
        const itemIndex = parseInt(params['itemIndex'], 10);
        
        // Load the created item
        this.apiService.get<any>(`/items/${itemId}`).subscribe({
          next: (newItem) => {
            // Add to available items if not already present
            const existingItem = this.availableItems.find(item => item.id === itemId);
            if (!existingItem) {
              this.availableItems.push(newItem);
            }
            
            // Select the newly created item in the specified index
            if (itemIndex >= 0 && itemIndex < this.itemsArray.length) {
              this.onItemSelected({ option: { value: newItem } }, itemIndex);
              this.toastService.success('Success', 'Item created and added to bill');
            }
            
            // Clean up query params
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: {}
            });
          },
          error: (err) => {
            this.toastService.error('Error', 'Failed to load created item');
          }
        });
      }
    });

    // Party search debounce
    this.partySearch$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(searchTerm => {
      this.filterParties(searchTerm);
    });
  }

  ngAfterViewInit() {
    // Animation setup if needed
  }

  loadParties() {
    // Load parties for Purchase: SUPPLIER, CUSTOMER, and BOTH
    this.apiService.get<Party[]>('/parties/for-purchase').subscribe({
      next: (parties) => {
        this.availableParties = parties || [];
        this.filteredParties = parties || [];
      },
      error: (err) => {
        // Fallback: load all parties and filter to SUPPLIER, CUSTOMER, BOTH
        this.apiService.get<PageResponse<Party>>('/clients', { page: 0, size: 1000 }).subscribe({
          next: (response) => {
            const allowed = ['SUPPLIER', 'CUSTOMER', 'BOTH'];
            this.availableParties = (response.content || []).filter((p: any) => allowed.includes(p.partyType));
            this.filteredParties = this.availableParties;
          }
        });
      }
    });
  }

  loadItems() {
    this.apiService.get<PageResponse<Item>>('/items', { page: 0, size: 1000 }).subscribe({
      next: (response) => {
        this.availableItems = response.content;
        this.itemsArray.controls.forEach((_, index) => {
          this.filteredItems[index] = response.content;
        });
      }
    });
  }

  searchParty(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.partySearchTerm = value;
    this.partySearch$.next(value);
  }

  filterParties(searchTerm: string) {
    this.partySearchTerm = searchTerm;
    if (!searchTerm) {
      this.filteredParties = this.availableParties;
      this.showCreateParty = false;
      return;
    }
    const term = searchTerm.toLowerCase();
    this.filteredParties = this.availableParties.filter(p => 
      p.partyName.toLowerCase().includes(term) || 
      (p.phone && p.phone.includes(term))
    );
    // Show "Create Party" option if no matching suppliers found
    this.showCreateParty = this.filteredParties.length === 0 && searchTerm.trim().length > 0;
  }

  onPartySelected(event: any) {
    if (this.isView) return; // Don't allow selection in view mode
    
    const value = event.option.value;
    
    // Handle "Create Party" option
    if (value === '__CREATE__') {
      this.router.navigate(['/clients/create'], { 
        queryParams: { 
          name: this.partySearchTerm,
          partyType: 'SUPPLIER',  // Pre-select SUPPLIER type
          returnUrl: this.router.url 
        } 
      });
      return;
    }
    
    // Handle normal party selection
    if (typeof value === 'object' && value.id) {
      const party: Party = value;
      this.purchaseForm.patchValue({
        partyId: party.id,
        partySearch: party.partyName,
        partyPhone: party.phone || ''
      });
      this.showCreateParty = false;
    }
  }

  searchItem(event: Event, index: number) {
    const value = (event.target as HTMLInputElement).value;
    if (!value) {
      this.filteredItems[index] = this.availableItems;
      return;
    }
    const term = value.toLowerCase();
    this.filteredItems[index] = this.availableItems.filter(item =>
      item.itemName.toLowerCase().includes(term) ||
      item.itemCode.toLowerCase().includes(term)
    );
  }

  showCreateItemOption(index: number): boolean {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const searchValue = itemGroup.get('itemSearch')?.value || '';
    if (!searchValue || searchValue.trim().length === 0) {
      return false;
    }
    const term = searchValue.toLowerCase();
    const hasMatch = this.availableItems.some(item =>
      item.itemName.toLowerCase().includes(term) ||
      item.itemCode.toLowerCase().includes(term)
    );
    return !hasMatch && searchValue.trim().length > 0;
  }

  getItemSearchValue(index: number): string {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    return itemGroup.get('itemSearch')?.value || '';
  }

  openCreateItemDialog(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const searchValue = itemGroup.get('itemSearch')?.value || '';
    
    // Navigate to Items create page with query parameters
    this.router.navigate(['/master/items/create'], {
      queryParams: {
        name: searchValue,
        returnUrl: this.router.url,
        itemIndex: index.toString()
      }
    });
  }

  onItemSelected(event: any, index: number) {
    const item: Item = event.option.value;
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    
    // Load full item details to get category
    this.apiService.get<any>(`/items/${item.id}`).subscribe({
      next: (fullItem) => {
        const itemCode = fullItem.itemCode || item.itemCode;
        const itemId = fullItem.id || item.id;
        
        // Check if this item already exists in the items array (by itemCode or itemId)
        const existingIndex = this.findExistingItemIndex(itemCode, itemId, index);
        
        if (existingIndex !== -1 && existingIndex !== index) {
          // Item already exists in another row - merge by incrementing quantity
          const existingGroup = this.itemsArray.at(existingIndex) as FormGroup;
          const currentQty = existingGroup.get('quantity')?.value || 0;
          const newQty = currentQty + (itemGroup.get('quantity')?.value || 1);
          existingGroup.patchValue({ quantity: newQty });
          this.calculateItemAmount(existingIndex);
          
          // Remove the current row since we merged it
          this.itemsArray.removeAt(index);
          delete this.filteredItems[index];
          
          // Rebuild filteredItems array after removal
          this.rebuildFilteredItems();
          
          this.toastService.info('Info', 'Item quantity updated in existing row');
        } else {
          // New item or same row - update the current row
          itemGroup.patchValue({
            itemId: itemId,
            itemCode: itemCode,
            itemName: fullItem.itemName || item.itemName,
            itemSearch: fullItem.itemName || item.itemName,
            category: fullItem.category || '', // From Item master
            enCode: fullItem.enCode || '',
            price: fullItem.purchasePrice || item.purchasePrice || 0,
            taxPercent: Math.round(fullItem.taxRate || item.taxRate || 0),
            unit: fullItem.unit || item.unit || '' // From Item master, read-only
          });
          this.calculateItemAmount(index);
        }
      },
      error: () => {
        // Fallback to basic item data
        const itemCode = item.itemCode;
        const itemId = item.id;
        
        // Check if this item already exists in the items array
        const existingIndex = this.findExistingItemIndex(itemCode, itemId, index);
        
        if (existingIndex !== -1 && existingIndex !== index) {
          // Item already exists - merge by incrementing quantity
          const existingGroup = this.itemsArray.at(existingIndex) as FormGroup;
          const currentQty = existingGroup.get('quantity')?.value || 0;
          const newQty = currentQty + (itemGroup.get('quantity')?.value || 1);
          existingGroup.patchValue({ quantity: newQty });
          this.calculateItemAmount(existingIndex);
          
          // Remove the current row since we merged it
          this.itemsArray.removeAt(index);
          delete this.filteredItems[index];
          this.rebuildFilteredItems();
          
          this.toastService.info('Info', 'Item quantity updated in existing row');
        } else {
          // New item or same row - update the current row
          itemGroup.patchValue({
            itemId: itemId,
            itemCode: itemCode,
            itemName: item.itemName,
            itemSearch: item.itemName,
            category: '',
            enCode: item.enCode || '',
            price: item.purchasePrice || 0,
            taxPercent: Math.round(item.taxRate || 0),
            unit: item.unit || ''
          });
          this.calculateItemAmount(index);
        }
      }
    });
  }
  
  /**
   * Find if an item already exists in the items array (excluding the current index)
   * Returns the index if found, -1 if not found
   */
  private findExistingItemIndex(itemCode: string, itemId: string, currentIndex: number): number {
    for (let i = 0; i < this.itemsArray.length; i++) {
      if (i === currentIndex) continue; // Skip current row
      
      const itemGroup = this.itemsArray.at(i) as FormGroup;
      const existingItemCode = itemGroup.get('itemCode')?.value;
      const existingItemId = itemGroup.get('itemId')?.value;
      
      // Match by itemCode (preferred) or itemId (fallback)
      if (itemCode && existingItemCode && itemCode.trim() === existingItemCode.trim()) {
        return i;
      }
      if (itemId && existingItemId && itemId.trim() === existingItemId.trim()) {
        return i;
      }
    }
    return -1;
  }
  
  /**
   * Rebuild filteredItems array after item removal
   */
  private rebuildFilteredItems() {
    const newFilteredItems: { [key: number]: Item[] } = {};
    for (let i = 0; i < this.itemsArray.length; i++) {
      newFilteredItems[i] = this.filteredItems[i] || this.availableItems;
    }
    this.filteredItems = newFilteredItems;
  }

  addItem() {
    const itemGroup = this.fb.group({
      itemId: [''],
      itemCode: [''],
      itemName: [''],
      itemSearch: [''],
      category: [''], // From Item master
      enCode: [''],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unit: [''], // From Item master, read-only
      price: [0, [Validators.required, Validators.min(0)]],
      taxPercent: [0, [Validators.min(0), Validators.max(100)]]
    });
    this.itemsArray.push(itemGroup);
    this.filteredItems[this.itemsArray.length - 1] = this.availableItems;
  }
  
  preventDecimalInput(event: KeyboardEvent) {
    if (event.key === '.' || event.key === ',' || event.key === 'e' || event.key === 'E' || event.key === '+' || event.key === '-') {
      event.preventDefault();
    }
  }
  
  getSelectedItemUnit(index: number): string {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const itemId = itemGroup.get('itemId')?.value;
    if (itemId) {
      const item = this.availableItems.find(i => i.id === itemId);
      return item?.unit || '';
    }
    return '';
  }

  removeItem(index: number) {
    this.itemsArray.removeAt(index);
    delete this.filteredItems[index];
    this.calculateTotals();
  }

  calculateItemAmount(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const quantity = itemGroup.get('quantity')?.value || 0;
    const price = itemGroup.get('price')?.value || 0;
    const taxPercent = itemGroup.get('taxPercent')?.value || 0;
    
    const amount = quantity * price;
    const taxAmount = amount * (taxPercent / 100);
    // Store calculated values (will be used in submit)
  }

  getItemAmount(index: number): number {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const quantity = itemGroup.get('quantity')?.value || 0;
    const price = itemGroup.get('price')?.value || 0;
    const taxPercent = itemGroup.get('taxPercent')?.value || 0;
    
    const amount = quantity * price;
    const taxAmount = amount * (taxPercent / 100);
    return amount + taxAmount;
  }

  getTotalQuantity(): number {
    return this.itemsArray.controls.reduce((sum, control) => {
      return sum + (control.get('quantity')?.value || 0);
    }, 0);
  }

  getTotalAmount(): number {
    return this.itemsArray.controls.reduce((sum, control) => {
      const index = this.itemsArray.controls.indexOf(control);
      return sum + this.getItemAmount(index);
    }, 0);
  }

  calculateTotals() {
    // Totals are calculated on-the-fly in getTotalAmount()
    this.calculateOutstandingAmount();
  }
  
  onPaymentStatusChange() {
    const paymentStatus = this.purchaseForm.get('paymentStatus')?.value;
    
    const totalAmount = this.getTotalAmount();
    const paymentType = this.purchaseForm.get('paymentType')?.value;
    if (paymentStatus === 'PAID') {
      if (paymentType === 'CASH') {
        this.purchaseForm.patchValue({ paidAmount: totalAmount, paidCashAmount: totalAmount, paidOnlineAmount: 0, outstandingAmount: 0 });
      } else {
        this.purchaseForm.patchValue({ paidAmount: totalAmount, paidCashAmount: 0, paidOnlineAmount: totalAmount, outstandingAmount: 0 });
      }
    } else if (paymentStatus === 'UNPAID') {
      this.purchaseForm.patchValue({ paidAmount: 0, paidCashAmount: 0, paidOnlineAmount: 0, outstandingAmount: totalAmount });
    } else if (paymentStatus === 'PARTIAL') {
      this.purchaseForm.patchValue({ paidCashAmount: 0, paidOnlineAmount: 0 });
      this.onPartialPaymentChange();
    }
  }
  
  onPartialPaymentChange() {
    const totalAmount = this.getTotalAmount();
    let cash = Number(this.purchaseForm.get('paidCashAmount')?.value || 0);
    let online = Number(this.purchaseForm.get('paidOnlineAmount')?.value || 0);
    if (cash + online > totalAmount) {
      this.toastService.error('Validation Error', 'Cash + Online cannot exceed total bill amount.');
      if (cash > totalAmount) {
        cash = totalAmount;
        online = 0;
        this.purchaseForm.patchValue({ paidCashAmount: cash, paidOnlineAmount: 0 }, { emitEvent: false });
      } else {
        online = Math.max(0, totalAmount - cash);
        this.purchaseForm.patchValue({ paidOnlineAmount: online }, { emitEvent: false });
      }
    }
    const paidAmount = cash + online;
    const outstandingAmount = Math.max(0, totalAmount - paidAmount);
    this.purchaseForm.patchValue({ paidAmount, outstandingAmount }, { emitEvent: false });
    if (paidAmount <= 0) {
      this.purchaseForm.patchValue({ paymentStatus: 'UNPAID' });
    } else if (paidAmount >= totalAmount) {
      this.purchaseForm.patchValue({ paymentStatus: 'PAID', paidAmount: totalAmount, outstandingAmount: 0 });
    } else {
      this.purchaseForm.patchValue({ paymentStatus: 'PARTIAL' });
    }
  }
  
  calculateOutstandingAmount() {
    const paymentStatus = this.purchaseForm.get('paymentStatus')?.value;
    const totalAmount = this.getTotalAmount();
    const paidAmount = paymentStatus === 'PARTIAL'
      ? (Number(this.purchaseForm.get('paidCashAmount')?.value || 0) + Number(this.purchaseForm.get('paidOnlineAmount')?.value || 0))
      : (this.purchaseForm.get('paidAmount')?.value || 0);
    
    if (paidAmount > totalAmount) {
      this.toastService.error('Validation Error', 'Paid amount cannot exceed total bill amount.');
      this.purchaseForm.patchValue({ paidAmount: totalAmount }, { emitEvent: false });
      return;
    }
    
    const outstandingAmount = Math.max(0, totalAmount - paidAmount);
    this.purchaseForm.patchValue({ outstandingAmount: outstandingAmount, paidAmount }, { emitEvent: false });
    
    if (paidAmount <= 0) {
      this.purchaseForm.patchValue({ paymentStatus: 'UNPAID' });
    } else if (paidAmount >= totalAmount) {
      this.purchaseForm.patchValue({ paymentStatus: 'PAID', paidAmount: totalAmount, outstandingAmount: 0 });
    } else {
      this.purchaseForm.patchValue({ paymentStatus: 'PARTIAL' });
    }
  }
  
  validatePartialPayment(): boolean {
    const totalAmount = this.getTotalAmount();
    const paymentStatus = this.purchaseForm.get('paymentStatus')?.value;
    if (paymentStatus === 'PARTIAL') {
      const cash = Number(this.purchaseForm.get('paidCashAmount')?.value || 0);
      const online = Number(this.purchaseForm.get('paidOnlineAmount')?.value || 0);
      if (cash + online > totalAmount) {
        this.toastService.error('Validation Error', 'Cash + Online cannot exceed total bill amount.');
        return false;
      }
      return true;
    }
    const paidAmount = this.purchaseForm.get('paidAmount')?.value || 0;
    if (paidAmount > totalAmount) {
      this.toastService.error('Validation Error', 'Paid amount cannot exceed total bill amount.');
      return false;
    }
    return true;
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      this.uploadedFile = file;
      this.uploadedFileName = file.name;
      this.uploadingFile = true;

      // Convert file to Base64
      const reader = new FileReader();
      reader.onload = () => {
        const base64String = reader.result as string;
        // Store Base64 data in form
        this.purchaseForm.patchValue({
          uploadedBillFile: base64String
        });
        this.uploadingFile = false;
        this.toastService.success('Success', 'File uploaded successfully');
      };
      reader.onerror = () => {
        this.uploadingFile = false;
        this.toastService.error('Error', 'Failed to read file');
      };
      reader.readAsDataURL(file);
    }
  }

  removeFile() {
    this.uploadedFile = null;
    this.uploadedFileName = null;
    this.uploadedBillFileUrl = null;
    this.purchaseForm.patchValue({
      uploadedBillFile: ''
    });
  }

  downloadUploadedFile() {
    if (!this.uploadedBillFileUrl) return;
    
    const link = document.createElement('a');
    link.href = this.uploadedBillFileUrl;
    link.download = this.uploadedFileName || 'purchase-bill-document';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  viewUploadedFile() {
    if (!this.uploadedBillFileUrl) return;
    window.open(this.uploadedBillFileUrl, '_blank');
  }

  isImageOrPdf(): boolean {
    if (!this.uploadedBillFileUrl) return false;
    const url = this.uploadedBillFileUrl.toLowerCase();
    return url.includes('image/') || url.includes('application/pdf');
  }

  loadBill(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/purchase-bills/${id}`).subscribe({
      next: (bill) => {
        this.loading = false;
        this.lastUpdatedBy = bill.lastUpdatedBy ?? '';
        
        // Find party object
        const party = this.availableParties.find(p => p.id === bill.partyId);
        
        this.purchaseForm.patchValue({
          partyId: bill.partyId,
          partySearch: bill.partyName || (party?.partyName || ''),
          partyPhone: bill.partyPhone || (party?.phone || ''),
          purchaseEntryNo: bill.purchaseEntryNo || '',
          billNumber: bill.billNumber,
          billDate: new Date(bill.billDate),
          paymentType: bill.paymentType || 'CASH',
          paymentStatus: bill.paymentStatus || 'UNPAID',
          onlinePaymentMethod: bill.onlinePaymentMethod || '',
          onlinePaymentReference: bill.onlinePaymentReference || '',
          paidAmount: bill.paidAmount || 0,
          paidCashAmount: bill.paidCashAmount != null ? Number(bill.paidCashAmount) : 0,
          paidOnlineAmount: bill.paidOnlineAmount != null ? Number(bill.paidOnlineAmount) : 0,
          outstandingAmount: bill.outstandingAmount || 0,
          notes: bill.notes || '',
          uploadedBillFile: bill.uploadedBillFile || ''
        });

        // Set uploaded file URL if exists
        if (bill.uploadedBillFile) {
          this.uploadedBillFileUrl = bill.uploadedBillFile;
          // Extract filename from data URI or use default
          if (bill.uploadedBillFile.includes('data:')) {
            const match = bill.uploadedBillFile.match(/filename=([^;]+)/);
            this.uploadedFileName = match ? match[1] : 'uploaded-document';
          } else {
            this.uploadedFileName = 'uploaded-document';
          }
        } else {
          this.uploadedBillFileUrl = null;
          this.uploadedFileName = null;
        }

        // Load items
        this.itemsArray.clear();
        if (bill.items && bill.items.length > 0) {
          bill.items.forEach((item: any) => {
            const itemGroup = this.fb.group({
              itemId: [item.itemId],
              itemCode: [item.itemCode],
              itemName: [item.itemName],
              itemSearch: [item.itemName],
              category: [item.category || ''], // From Item master
              enCode: [item.enCode || ''],
              quantity: [Math.round(item.quantity || 1), [Validators.required, Validators.min(1)]],
              unit: [item.unit || ''], // From Item master, read-only
              price: [item.price, [Validators.required, Validators.min(0)]],
              taxPercent: [Math.round(item.taxPercent || 0), [Validators.min(0), Validators.max(100)]]
            });
            this.itemsArray.push(itemGroup);
            this.filteredItems[this.itemsArray.length - 1] = this.availableItems;
          });
        } else {
          this.addItem();
        }
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load purchase bill');
      }
    });
  }

  onSubmit() {
    if (this.purchaseForm.valid && this.itemsArray.length > 0) {
      // Validate partial payment before submitting
      if (!this.validatePartialPayment()) {
        return;
      }
      
      this.loading = true;
      
      const formValue = this.purchaseForm.value;
      const items = this.itemsArray.controls.map(control => {
        const itemGroup = control as FormGroup;
        const quantity = itemGroup.get('quantity')?.value || 0;
        const price = itemGroup.get('price')?.value || 0;
        const taxPercent = itemGroup.get('taxPercent')?.value || 0;
        const amount = quantity * price;
        const taxAmount = amount * (taxPercent / 100);
        
        return {
          itemId: itemGroup.get('itemId')?.value,
          itemCode: itemGroup.get('itemCode')?.value,
          itemName: itemGroup.get('itemName')?.value,
          category: itemGroup.get('category')?.value || '', // From Item master
          enCode: itemGroup.get('enCode')?.value || '',
          quantity: Math.round(quantity), // Integer only
          unit: itemGroup.get('unit')?.value || '', // From Item master
          price: price,
          taxPercent: Math.round(taxPercent), // Integer only
          taxAmount: taxAmount,
          amount: amount + taxAmount
        };
      });

      const billData = {
        purchaseEntryNo: formValue.purchaseEntryNo || null, // Optional, auto-generated if empty
        billNumber: formValue.billNumber, // Purchase Invoice Number (mandatory)
        billDate: formValue.billDate,
        partyId: formValue.partyId,
        paymentType: formValue.paymentType,
        paymentStatus: formValue.paymentStatus || 'UNPAID',
        onlinePaymentMethod: formValue.onlinePaymentMethod || null,
        onlinePaymentReference: formValue.onlinePaymentReference || null,
        paidAmount: formValue.paidAmount || 0,
        paidCashAmount: formValue.paidCashAmount != null ? formValue.paidCashAmount : 0,
        paidOnlineAmount: formValue.paidOnlineAmount != null ? formValue.paidOnlineAmount : 0,
        outstandingAmount: formValue.outstandingAmount || 0,
        items: items,
        notes: formValue.notes,
        uploadedBillFile: formValue.uploadedBillFile
      };

      const request = this.isEdit && this.billId
        ? this.apiService.put<any>('/purchase-bills', this.billId, billData)
        : this.apiService.post<any>('/purchase-bills', billData);

      request.subscribe({
        next: (bill) => {
          this.loading = false;
          this.toastService.success('Success', `Purchase bill ${this.isEdit ? 'updated' : 'created'} successfully`);
          setTimeout(() => {
            this.router.navigate(['/purchase-expense/purchase-bills']);
          }, 500);
        },
        error: (error) => {
          this.loading = false;
          this.toastService.error('Error', `Failed to ${this.isEdit ? 'update' : 'create'} purchase bill`);
        }
      });
    }
  }

  saveAndPrint() {
    this.onSubmit();
    // Print will be handled after successful save
  }

  editBill() {
    if (this.billId) {
      this.router.navigate(['/purchase-expense/purchase-bills/edit', this.billId]);
    }
  }

  printBill() {
    if (!this.billId) {
      this.toastService.warning('Warning', 'Cannot print: Bill not saved yet');
      return;
    }
    
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    // Use /print endpoint and fetch with authentication headers
    this.http.get(`${apiUrl}/purchase-bills/${this.billId}/print`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        // Create blob URL and open in new window for printing
        const url = window.URL.createObjectURL(blob);
        const printWindow = window.open(url, '_blank');
        if (printWindow) {
          printWindow.onload = () => {
            printWindow.print();
          };
        } else {
          this.toastService.error('Error', 'Please allow popups to print');
        }
        this.toastService.success('Success', 'PDF generated successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to generate PDF');
        console.error('PDF generation error:', err);
      }
    });
  }

  cancel() {
    this.router.navigate(['/purchase-expense/purchase-bills']);
  }
}

