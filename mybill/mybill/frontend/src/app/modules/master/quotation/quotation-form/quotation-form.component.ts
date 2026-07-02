import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

interface Party {
  id: string;
  partyName: string;
  phone?: string;
  address?: string;
  partyType?: string;
}

interface Item {
  id: string;
  itemCode: string;
  itemName: string;
  sellingPrice: number;
}

@Component({
  selector: 'app-quotation-form',
  template: `
    <div class="page-container" #pageContainer>
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-6xl mx-auto" #card>
        <mat-card-header>
          <mat-card-title>{{ isView ? 'View' : (isEdit ? 'Edit' : 'Create') }} Quotation</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="quotationForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Basic Information Section -->
            <div class="form-section">
              <h3 class="section-title">Basic Information</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Quotation No</mat-label>
                  <input matInput formControlName="quotationNumber" 
                         placeholder="Leave empty for auto-generation"
                         [disabled]="isView">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Quotation Date *</mat-label>
                  <input matInput [matDatepicker]="picker" formControlName="quotationDate" required [disabled]="isView">
                  <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                  <mat-datepicker #picker></mat-datepicker>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Party *</mat-label>
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
                        <small *ngIf="party.phone">{{ party.phone }}</small>
                      </div>
                    </mat-option>
                  </mat-autocomplete>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Delivery Date</mat-label>
                  <input matInput [matDatepicker]="deliveryPicker" formControlName="deliveryDate" 
                         [min]="getMinDeliveryDate()" [disabled]="isView">
                  <mat-datepicker-toggle matSuffix [for]="deliveryPicker"></mat-datepicker-toggle>
                  <mat-datepicker #deliveryPicker></mat-datepicker>
                  <mat-error *ngIf="quotationForm.get('deliveryDate')?.hasError('deliveryDateInvalid')">
                    Delivery Date must be same as or later than Quotation Date
                  </mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Quotation Valid Till (Days)</mat-label>
                  <input matInput type="number" formControlName="quotationValidTillDays" 
                         min="1" [disabled]="isView" placeholder="e.g., 30">
                </mat-form-field>
              </div>
              
              <!-- GST Option -->
              <div class="mt-4">
                <mat-checkbox formControlName="gstRequired" (change)="onGstRequiredChange()" [disabled]="isView">
                  GST Required
                </mat-checkbox>
                <small class="ml-2 text-gray-500">{{ quotationForm.get('gstRequired')?.value ? 'GST will be calculated' : 'No GST will be applied' }}</small>
              </div>
            </div>

            <!-- Shipping To Section -->
            <div class="form-section">
              <h3 class="section-title">Shipping To</h3>
              <mat-checkbox formControlName="sameAsPartyAddress" (change)="onSameAsPartyAddressChange()" [disabled]="isView">
                Same as Party Address
              </mat-checkbox>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Party Name</mat-label>
                  <input matInput formControlName="shippingToPartyName" [disabled]="isView || quotationForm.get('sameAsPartyAddress')?.value">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Address</mat-label>
                  <textarea matInput formControlName="shippingToAddress" rows="3" 
                            [disabled]="isView || quotationForm.get('sameAsPartyAddress')?.value"></textarea>
                </mat-form-field>
              </div>
            </div>

            <!-- Items Section -->
            <div class="form-section">
              <div class="flex justify-between items-center mb-4">
                <h3 class="section-title">Items</h3>
                <button type="button" mat-button (click)="addItem()" class="btn-secondary" [disabled]="isView">
                  <mat-icon>add</mat-icon>
                  <span>Add Item</span>
                </button>
              </div>
              
              <div formArrayName="items" class="space-y-4">
                <div *ngFor="let item of itemsArray.controls; let i = index" 
                     [formGroupName]="i" 
                     class="item-row">
                  <div class="grid grid-cols-12 gap-4">
                    <mat-form-field appearance="outline" class="col-span-4">
                      <mat-label>Item</mat-label>
                      <input matInput formControlName="itemSearch"
                             [matAutocomplete]="itemAuto"
                             (input)="searchItem($event, i)"
                             placeholder="Search item..."
                             [disabled]="isView">
                      <mat-autocomplete #itemAuto="matAutocomplete" (optionSelected)="onItemSelected($event, i)">
                        <mat-option *ngFor="let item of getFilteredItems(i)" [value]="item">
                          <div class="autocomplete-option">
                            <span>{{ item.itemName }}</span>
                            <small>{{ item.itemCode }} - ₹{{ item.sellingPrice }}</small>
                          </div>
                        </mat-option>
                      </mat-autocomplete>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="col-span-2">
                      <mat-label>Quantity</mat-label>
                      <input matInput type="number" formControlName="quantity" 
                             (change)="calculateItemTotal(i)" step="1" min="1" [disabled]="isView">
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="col-span-2">
                      <mat-label>Unit Price</mat-label>
                      <input matInput type="number" formControlName="unitPrice" 
                             (change)="calculateItemTotal(i)" step="0.01" [disabled]="isView">
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="col-span-2">
                      <mat-label>Total</mat-label>
                      <input matInput formControlName="totalAmount" [disabled]="true">
                    </mat-form-field>

                    <div class="col-span-2 flex items-center">
                      <button type="button" mat-icon-button (click)="removeItem(i)" [disabled]="isView" color="warn">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Totals Section -->
            <div class="form-section">
              <h3 class="section-title">Totals</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Subtotal</mat-label>
                  <input matInput formControlName="subtotal" [disabled]="true">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Discount (%)</mat-label>
                  <input matInput type="number" formControlName="discountPercent" 
                         (change)="calculateTotals()" step="0.01" min="0" max="100" [disabled]="isView">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Discount Amount</mat-label>
                  <input matInput formControlName="discountAmount" 
                         (change)="calculateTotals()" step="0.01" [disabled]="isView">
                </mat-form-field>

                <!-- GST Breakdown - Only show when GST is required -->
                <ng-container *ngIf="quotationForm.get('gstRequired')?.value">
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>CGST</mat-label>
                    <input matInput formControlName="cgst" [disabled]="true">
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>SGST</mat-label>
                    <input matInput formControlName="sgst" [disabled]="true">
                  </mat-form-field>
                  
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Total GST</mat-label>
                    <input matInput formControlName="taxAmount" [disabled]="true">
                  </mat-form-field>
                </ng-container>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Items Total Amount</mat-label>
                  <input matInput formControlName="totalAmount" [disabled]="true" class="font-bold">
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Notes</mat-label>
                <textarea matInput formControlName="notes" rows="3" [disabled]="isView"></textarea>
              </mat-form-field>
            </div>

            <!-- Actions -->
            <div class="flex gap-4 pt-4">
              <button *ngIf="!isView" mat-raised-button color="primary" type="submit" 
                      [disabled]="quotationForm.invalid || loading"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">{{ isEdit ? 'Update' : 'Create' }}</span>
                <span *ngIf="loading">Processing...</span>
              </button>
              <button *ngIf="isView && quotationId" mat-raised-button color="primary" type="button" 
                      (click)="editQuotation()" class="btn-primary">
                Edit Quotation
              </button>
              <button *ngIf="quotationId" mat-raised-button color="accent" type="button" 
                      (click)="printQuotation()" class="btn-secondary">
                <mat-icon>print</mat-icon>
                <span>Print</span>
              </button>
              <button *ngIf="quotationId" mat-raised-button color="accent" type="button" 
                      (click)="downloadQuotation()" class="btn-secondary">
                <mat-icon>download</mat-icon>
                <span>Download PDF</span>
              </button>
              <button mat-button type="button" (click)="cancel()">{{ isView ? 'Back' : 'Cancel' }}</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .form-section { margin-bottom: 32px; padding: 24px; background: #F9FAFB; border-radius: 8px; }
    .section-title { font-size: 16px; font-weight: 600; color: #1A1D2E; margin-bottom: 16px; }
    .item-row { padding: 16px; background: white; border-radius: 8px; border: 1px solid #E5E7EB; }
    .autocomplete-option { display: flex; flex-direction: column; }
    .autocomplete-option small { color: #6B7280; font-size: 11px; }
  `]
})
export class QuotationFormComponent implements OnInit, AfterViewInit {
  quotationForm: FormGroup;
  isEdit = false;
  isView = false;
  quotationId: string | null = null;
  loading = false;
  parties: Party[] = [];
  filteredParties: Party[] = [];
  availableItems: Item[] = [];
  filteredItems: Map<number, Item[]> = new Map();
  private partySearch$ = new Subject<string>();
  private itemSearch$ = new Subject<{term: string, index: number}>();
  companySettings: any = null;

  @ViewChild('pageContainer') pageContainer!: ElementRef;
  @ViewChild('card') card!: ElementRef;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private http: HttpClient
  ) {
    this.quotationForm = this.fb.group({
      quotationNumber: [''],
      quotationDate: [new Date(), Validators.required],
      partyId: ['', Validators.required],
      partySearch: [''],
      myAddress: [''],
      shippingToPartyName: [''],
      shippingToAddress: [''],
      sameAsPartyAddress: [false],
      deliveryDate: [null, [this.deliveryDateValidator.bind(this)]],
      quotationValidTillDays: [30],
      items: this.fb.array([], Validators.required),
      subtotal: [0],
      discountPercent: [0],
      discountAmount: [0],
      taxAmount: [0],
      cgst: [0],
      sgst: [0],
      totalAmount: [0],
      gstRequired: [true], // Default to GST required
      notes: ['']
    });
  }

  get itemsArray(): FormArray {
    return this.quotationForm.get('items') as FormArray;
  }

  ngOnInit() {
    this.loadParties();
    this.loadItems();
    this.loadCompanySettings();
    this.setupSearchSubscriptions();
    
    // Watch for quotation date changes to revalidate delivery date
    this.quotationForm.get('quotationDate')?.valueChanges.subscribe(() => {
      this.quotationForm.get('deliveryDate')?.updateValueAndValidity();
    });
    
    const id = this.route.snapshot.paramMap.get('id');
    const url = this.router.url;
    
    if (id) {
      this.quotationId = id;
      if (url.includes('/view/')) {
        this.isView = true;
        this.quotationForm.disable();
      } else {
        this.isEdit = true;
      }
      this.loadQuotation(id);
    }
  }

  ngAfterViewInit() {
    setTimeout(() => {
      if (this.pageContainer?.nativeElement) {
        // Animation can be added here
      }
    }, 100);
  }

  setupSearchSubscriptions() {
    this.partySearch$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(term => {
      if (term.length >= 2) {
        this.apiService.search<any>('/clients', term, 0, 10).subscribe({
          next: (response) => {
            this.filteredParties = response.content.filter((p: Party) => !p.partyType || p.partyType === 'CUSTOMER');
          },
          error: () => {
            this.filteredParties = [];
          }
        });
      } else {
        this.filteredParties = this.parties.slice(0, 10);
      }
    });

    this.itemSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged((prev, curr) => prev.term === curr.term && prev.index === curr.index)
    ).subscribe(({term, index}) => {
      if (term.length >= 2) {
        this.apiService.search<Item>('/items', term, 0, 10).subscribe({
          next: (response) => {
            this.filteredItems.set(index, response.content);
          },
          error: () => {
            this.filteredItems.set(index, []);
          }
        });
      } else {
        this.filteredItems.set(index, this.availableItems.slice(0, 10));
      }
    });
  }

  loadParties() {
    this.apiService.get<Party[]>('/parties/type/CUSTOMER').subscribe({
      next: (parties) => {
        this.parties = parties;
        this.filteredParties = this.parties.slice(0, 10);
      },
      error: () => {
        this.apiService.getPaged<Party>('/clients', 0, 100).subscribe({
          next: (response) => {
            this.parties = response.content.filter((p: Party) => !p.partyType || p.partyType === 'CUSTOMER');
            this.filteredParties = this.parties.slice(0, 10);
          }
        });
      }
    });
  }

  loadItems() {
    this.apiService.getPaged<Item>('/items', 0, 1000).subscribe({
      next: (response) => {
        this.availableItems = response.content;
      }
    });
  }

  loadCompanySettings() {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        this.companySettings = settings;
        if (settings && settings.myAddress) {
          this.quotationForm.patchValue({ myAddress: settings.myAddress });
        }
        if (settings && settings.footerForQuotation) {
          // Footer will be set on save
        }
      }
    });
  }

  searchParty(event: Event) {
    if (this.isView) return;
    const term = (event.target as HTMLInputElement).value;
    this.partySearch$.next(term);
  }

  onPartySelected(event: any) {
    if (this.isView) return;
    const value = event.option.value;
    if (typeof value === 'object' && value.id) {
      this.quotationForm.patchValue({
        partyId: value.id,
        partySearch: value.partyName
      });
      if (this.quotationForm.get('sameAsPartyAddress')?.value) {
        this.quotationForm.patchValue({
          shippingToPartyName: value.partyName,
          shippingToAddress: value.address || ''
        });
      }
    }
  }

  onSameAsPartyAddressChange() {
    if (this.quotationForm.get('sameAsPartyAddress')?.value) {
      const party = this.parties.find(p => p.id === this.quotationForm.get('partyId')?.value);
      if (party) {
        this.quotationForm.patchValue({
          shippingToPartyName: party.partyName,
          shippingToAddress: party.address || ''
        });
      }
    }
  }

  searchItem(event: Event, index: number) {
    if (this.isView) return;
    const term = (event.target as HTMLInputElement).value;
    this.itemSearch$.next({ term, index });
  }

  onItemSelected(event: any, index: number) {
    if (this.isView) return;
    const value = event.option.value;
    if (typeof value === 'object' && value.id) {
      const itemGroup = this.itemsArray.at(index) as FormGroup;
      const itemCode = value.itemCode || '';
      const itemId = value.id;
      
      // Check if this item already exists in the items array (by itemCode or itemId)
      const existingIndex = this.findExistingItemIndex(itemCode, itemId, index);
      
      if (existingIndex !== -1 && existingIndex !== index) {
        // Item already exists in another row - merge by incrementing quantity
        const existingGroup = this.itemsArray.at(existingIndex) as FormGroup;
        const currentQty = existingGroup.get('quantity')?.value || 0;
        const newQty = currentQty + (itemGroup.get('quantity')?.value || 1);
        existingGroup.patchValue({ quantity: newQty });
        this.calculateItemTotal(existingIndex);
        
        // Remove the current row since we merged it
        this.itemsArray.removeAt(index);
        this.filteredItems.delete(index);
        
        // Rebuild filteredItems map after removal
        this.rebuildFilteredItems();
        
        this.toastService.info('Info', 'Item quantity updated in existing row');
      } else {
        // New item or same row - update the current row
        itemGroup.patchValue({
          itemId: itemId,
          itemSearch: value.itemName,
          unitPrice: value.sellingPrice || 0
        });
        this.calculateItemTotal(index);
      }
    }
  }
  
  /**
   * Find if an item already exists in the items array (excluding the current index)
   * Returns the index if found, -1 if not found
   */
  private findExistingItemIndex(itemCode: string, itemId: string, currentIndex: number): number {
    for (let i = 0; i < this.itemsArray.length; i++) {
      if (i === currentIndex) continue; // Skip current row
      
      const itemGroup = this.itemsArray.at(i) as FormGroup;
      const existingItemId = itemGroup.get('itemId')?.value;
      
      // Match by itemId (since quotation items don't store itemCode in form)
      // We can also check by getting itemCode from availableItems
      if (itemId && existingItemId && itemId.trim() === existingItemId.trim()) {
        return i;
      }
      
      // Also check by itemCode if we can find it in availableItems
      if (itemCode) {
        const existingItem = this.availableItems.find(item => item.id === existingItemId);
        if (existingItem && existingItem.itemCode && existingItem.itemCode.trim() === itemCode.trim()) {
          return i;
        }
      }
    }
    return -1;
  }
  
  /**
   * Rebuild filteredItems map after item removal
   * Since indices shift after removal, we rebuild from scratch
   */
  private rebuildFilteredItems() {
    const newFilteredItems = new Map<number, Item[]>();
    for (let i = 0; i < this.itemsArray.length; i++) {
      // Use default filtered items for all rows after rebuild
      newFilteredItems.set(i, this.availableItems.slice(0, 10));
    }
    this.filteredItems = newFilteredItems;
  }

  getFilteredItems(index: number): Item[] {
    return this.filteredItems.get(index) || this.availableItems.slice(0, 10);
  }

  addItem() {
    const itemGroup = this.fb.group({
      itemId: ['', Validators.required],
      itemSearch: [''],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unitPrice: [0, Validators.required],
      totalAmount: [0]
    });
    const index = this.itemsArray.length;
    this.itemsArray.push(itemGroup);
    this.filteredItems.set(index, this.availableItems.slice(0, 10));
  }

  removeItem(index: number) {
    this.itemsArray.removeAt(index);
    this.calculateTotals();
  }

  calculateItemTotal(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const quantity = itemGroup.get('quantity')?.value || 0;
    const unitPrice = itemGroup.get('unitPrice')?.value || 0;
    const total = quantity * unitPrice;
    itemGroup.patchValue({ totalAmount: total }, { emitEvent: false });
    this.calculateTotals();
  }

  calculateTotals() {
    let subtotal = 0;
    let totalTax = 0;
    const gstRequired = this.quotationForm.get('gstRequired')?.value;
    
    this.itemsArray.controls.forEach(control => {
      const itemTotal = control.get('totalAmount')?.value || 0;
      subtotal += itemTotal;
      
      // Calculate tax for each item if GST is required
      if (gstRequired) {
        const itemId = control.get('itemId')?.value;
        const item = this.availableItems.find(i => i.id === itemId);
        if (item && (item as any).taxRate) {
          const itemTax = itemTotal * ((item as any).taxRate / 100);
          totalTax += itemTax;
        } else {
          // Default 18% if no taxRate
          totalTax += itemTotal * 0.18;
        }
      }
    });

    const discountPercent = this.quotationForm.get('discountPercent')?.value || 0;
    const discountAmount = this.quotationForm.get('discountAmount')?.value || 0;

    let finalDiscount = discountAmount;
    if (discountPercent > 0 && discountAmount === 0) {
      finalDiscount = (subtotal * discountPercent) / 100;
    }

    // GST breakdown (50/50 CGST/SGST)
    let cgst = 0;
    let sgst = 0;
    if (gstRequired && totalTax > 0) {
      cgst = Math.round(totalTax / 2 * 100) / 100;
      sgst = Math.round((totalTax - cgst) * 100) / 100;
    }

    // Calculate total
    let total = subtotal - finalDiscount;
    if (gstRequired) {
      total += totalTax;
    }

    this.quotationForm.patchValue({
      subtotal: Math.round(subtotal * 100) / 100,
      discountAmount: Math.round(finalDiscount * 100) / 100,
      taxAmount: Math.round(totalTax * 100) / 100,
      cgst: cgst,
      sgst: sgst,
      totalAmount: Math.round(total * 100) / 100
    }, { emitEvent: false });
  }
  
  onGstRequiredChange() {
    this.calculateTotals();
  }
  
  loadQuotation(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/quotations/${id}`).subscribe({
      next: (quotation) => {
        this.quotationForm.patchValue({
          quotationNumber: quotation.quotationNumber,
          quotationDate: new Date(quotation.quotationDate),
          partyId: quotation.partyId,
          partySearch: quotation.partyName,
          myAddress: quotation.myAddress || this.companySettings?.myAddress || '',
          shippingToPartyName: quotation.shippingToPartyName || '',
          shippingToAddress: quotation.shippingToAddress || '',
          sameAsPartyAddress: quotation.sameAsPartyAddress || false,
          deliveryDate: quotation.deliveryDate ? new Date(quotation.deliveryDate) : null,
          quotationValidTillDays: quotation.quotationValidTillDays || 30,
          subtotal: quotation.subtotal || 0,
          discountPercent: quotation.discountPercent || 0,
          discountAmount: quotation.discountAmount || 0,
          taxAmount: quotation.taxAmount || 0,
          cgst: quotation.cgst || 0,
          sgst: quotation.sgst || 0,
          totalAmount: quotation.totalAmount || 0,
          gstRequired: quotation.gstRequired !== false, // Default to true for backward compatibility
          notes: quotation.notes || ''
        });

        quotation.items?.forEach((item: any) => {
          this.addItem();
          const lastIndex = this.itemsArray.length - 1;
          const itemGroup = this.itemsArray.at(lastIndex) as FormGroup;
          itemGroup.patchValue({
            itemId: item.itemId,
            itemSearch: item.itemName,
            quantity: item.quantity,
            unitPrice: item.unitPrice,
            totalAmount: item.totalAmount
          });
        });

        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load quotation');
        this.loading = false;
      }
    });
  }

  onSubmit() {
    if (this.quotationForm.invalid) return;

    this.loading = true;
    const formValue = this.quotationForm.value;

    const items = this.itemsArray.controls.map(control => {
      const itemGroup = control as FormGroup;
      return {
        itemId: itemGroup.get('itemId')?.value,
        itemCode: this.availableItems.find(i => i.id === itemGroup.get('itemId')?.value)?.itemCode || '',
        itemName: itemGroup.get('itemSearch')?.value,
        quantity: itemGroup.get('quantity')?.value,
        unitPrice: itemGroup.get('unitPrice')?.value,
        totalAmount: itemGroup.get('totalAmount')?.value
      };
    });

    const quotationData = {
      quotationNumber: formValue.quotationNumber || null,
      quotationDate: formValue.quotationDate,
      partyId: formValue.partyId,
      myAddress: this.companySettings?.myAddress || formValue.myAddress || '',
      shippingToPartyName: formValue.shippingToPartyName || '',
      shippingToAddress: formValue.shippingToAddress || '',
      sameAsPartyAddress: formValue.sameAsPartyAddress || false,
      deliveryDate: formValue.deliveryDate || null,
      quotationValidTillDays: formValue.quotationValidTillDays || 30,
      items: items,
      subtotal: formValue.subtotal,
      discountPercent: formValue.discountPercent || 0,
      discountAmount: formValue.discountAmount || 0,
      taxAmount: formValue.taxAmount || 0,
      cgst: formValue.cgst || 0,
      sgst: formValue.sgst || 0,
      totalAmount: formValue.totalAmount,
      gstRequired: formValue.gstRequired,
      notes: formValue.notes || '',
      footerSlogan: this.companySettings?.footerForQuotation || ''
    };

    if (this.isEdit && this.quotationId) {
      this.apiService.put('/quotations', this.quotationId, quotationData).subscribe({
        next: () => {
          this.toastService.success('Success', 'Quotation updated successfully');
          this.router.navigate(['/quotations']);
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to update quotation');
          this.loading = false;
        }
      });
    } else {
      this.apiService.post('/quotations', quotationData).subscribe({
        next: () => {
          this.toastService.success('Success', 'Quotation created successfully');
          this.router.navigate(['/quotations']);
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to create quotation');
          this.loading = false;
        }
      });
    }
  }

  getMinDeliveryDate(): Date {
    const quotationDate = this.quotationForm.get('quotationDate')?.value;
    if (quotationDate) {
      return new Date(quotationDate);
    }
    return new Date();
  }
  
  deliveryDateValidator(control: AbstractControl): ValidationErrors | null {
    const deliveryDate = control.value;
    const quotationDate = this.quotationForm?.get('quotationDate')?.value;
    
    if (!deliveryDate || !quotationDate) {
      return null; // Let required validator handle empty values
    }
    
    const delivery = new Date(deliveryDate);
    const quotation = new Date(quotationDate);
    
    // Reset time to compare dates only
    delivery.setHours(0, 0, 0, 0);
    quotation.setHours(0, 0, 0, 0);
    
    if (delivery < quotation) {
      return { deliveryDateInvalid: true };
    }
    
    return null;
  }
  
  editQuotation() {
    if (this.quotationId) {
      this.router.navigate(['/quotations/edit', this.quotationId]);
    }
  }
  
  printQuotation() {
    if (!this.quotationId) {
      this.toastService.warning('Warning', 'Cannot print: Quotation not saved yet');
      return;
    }
    
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    this.http.get(`${apiUrl}/quotations/${this.quotationId}/print`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        const url = window.URL.createObjectURL(blob);
        const printWindow = window.open(url, '_blank');
        if (printWindow) {
          printWindow.onload = () => {
            setTimeout(() => {
              printWindow.print();
              setTimeout(() => {
                window.URL.revokeObjectURL(url);
              }, 1000);
            }, 500);
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
  
  downloadQuotation() {
    if (!this.quotationId) {
      this.toastService.warning('Warning', 'Cannot download: Quotation not saved yet');
      return;
    }
    
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    this.http.get(`${apiUrl}/quotations/${this.quotationId}/pdf`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        let filename = `quotation-${this.quotationId}.pdf`;
        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition) {
          const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1].replace(/['"]/g, '');
          }
        }
        
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'PDF downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download PDF');
        console.error('PDF download error:', err);
      }
    });
  }
  
  cancel() {
    this.router.navigate(['/quotations']);
  }
}

