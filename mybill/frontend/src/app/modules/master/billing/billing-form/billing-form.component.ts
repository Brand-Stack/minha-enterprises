import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../../core/services/api.service';
import { AnimationService } from '../../../../core/services/animation.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PageResponse } from '../../../../core/services/api.service';

interface Party {
  id: string;
  partyName: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
  gstin?: string;
  partyType?: string;
}

interface Item {
  id: string;
  itemCode: string;
  itemName: string;
  enCode?: string; // EN Code for item identification
  sellingPrice: number;
  taxRate: number; // Used for GST calculation, not displayed in form
  stockQuantity: number;
  unit?: string;
}

@Component({
  selector: 'app-billing-form',
  template: `
    <div class="page-container" #pageContainer>
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-6xl mx-auto" #card>
        <mat-card-header>
          <mat-card-title class="dark:text-gray-100">{{ isView ? 'View' : (isEdit ? 'Edit' : 'Add') }} Transaction</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="billingForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Bill Type Selection -->
            <div class="form-section bill-type-section">
              <div class="bill-type-toggle-group">
                <button type="button" 
                        mat-raised-button 
                        [class.selected]="billingForm.get('billType')?.value === 'ESTIMATE'"
                        [class.not-selected]="billingForm.get('billType')?.value !== 'ESTIMATE'"
                        [color]="billingForm.get('billType')?.value === 'ESTIMATE' ? 'primary' : ''"
                        (click)="billingForm.patchValue({billType: 'ESTIMATE'}); calculateTotals()"
                        [disabled]="isView"
                        class="bill-type-toggle-btn estimate-btn">
                  <mat-icon class="btn-icon">receipt_long</mat-icon>
                  <div class="btn-content">
                    <div class="btn-title">Estimate</div>
                    <div class="btn-subtitle">No GST • Stock NOT Deducted</div>
                  </div>
                  <mat-icon class="check-icon" *ngIf="billingForm.get('billType')?.value === 'ESTIMATE'">check_circle</mat-icon>
                </button>
                <button type="button" 
                        mat-raised-button 
                        [class.selected]="billingForm.get('billType')?.value === 'GST'"
                        [class.not-selected]="billingForm.get('billType')?.value !== 'GST'"
                        [color]="billingForm.get('billType')?.value === 'GST' ? 'primary' : ''"
                        (click)="billingForm.patchValue({billType: 'GST'}); calculateTotals()"
                        [disabled]="isView"
                        class="bill-type-toggle-btn invoice-btn">
                  <mat-icon class="btn-icon">verified</mat-icon>
                  <div class="btn-content">
                    <div class="btn-title">Invoice</div>
                    <div class="btn-subtitle">With GST • Stock Deducted</div>
                  </div>
                  <mat-icon class="check-icon" *ngIf="billingForm.get('billType')?.value === 'GST'">check_circle</mat-icon>
                </button>
              </div>
            </div>

            <!-- Basic Information Section -->
            <div class="form-section">
              <h3 class="section-title">Basic Information</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Bill No</mat-label>
                  <input matInput formControlName="invoiceNumber" 
                         placeholder="Leave empty for auto-generation"
                         [disabled]="isView"
                         (blur)="validateBillNumber()">
                  <mat-hint>Leave empty to auto-generate (EST-XXX for Estimate, INV-XXX for GST), or enter manually</mat-hint>
                  <mat-error *ngIf="billingForm.get('invoiceNumber')?.hasError('duplicate')">
                    Bill No already exists for this bill type
                  </mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Bill Date *</mat-label>
                  <input matInput [matDatepicker]="picker" formControlName="invoiceDate" required [disabled]="isView">
                  <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                  <mat-datepicker #picker></mat-datepicker>
                  <mat-error *ngIf="billingForm.get('invoiceDate')?.hasError('required')">Date is required</mat-error>
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
                    <mat-option *ngIf="showCreateParty && !isView" [value]="'__CREATE__'" class="create-option">
                      <mat-icon>add_circle</mat-icon>
                      <span>Create new party "{{ partySearchTerm }}"</span>
                    </mat-option>
                  </mat-autocomplete>
                  <mat-error *ngIf="billingForm.get('partyId')?.hasError('required')">Party is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Payment Status {{ billingForm.get('billType')?.value === 'GST' ? '*' : '' }}</mat-label>
                  <mat-select formControlName="paymentStatus" [disabled]="isView" (selectionChange)="onPaymentStatusChange()">
                    <mat-option value="PENDING">PENDING</mat-option>
                    <mat-option value="PARTIAL">PARTIAL</mat-option>
                    <mat-option value="PAID">PAID</mat-option>
                  </mat-select>
                  <mat-hint *ngIf="billingForm.get('billType')?.value === 'ESTIMATE'">Record cash/online received so it shows in Cash In Hand</mat-hint>
                  <mat-error *ngIf="billingForm.get('billType')?.value === 'GST' && billingForm.get('paymentStatus')?.hasError('required')">Payment status is required</mat-error>
                </mat-form-field>

                <!-- PARTIAL: Cash Amount + Online Amount (cash + online <= total) -->
                <ng-container *ngIf="billingForm.get('modeOfPayment')?.value === 'PARTIAL'">
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Cash Amount</mat-label>
                    <input matInput type="number" formControlName="receivedCashAmount" 
                           (change)="onPartialPaymentChange()" step="0.01" min="0" [disabled]="isView">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Online Amount</mat-label>
                    <input matInput type="number" formControlName="receivedOnlineAmount" 
                           (change)="onPartialPaymentChange()" step="0.01" min="0" [disabled]="isView">
                  </mat-form-field>
                  <div class="text-sm text-gray-600 col-span-2" *ngIf="!isView">Cash + Online must not exceed total. Balance: ₹{{ calculateBalanceAmount() | number:'1.2-2' }}</div>
                </ng-container>
                <!-- Single Paid Amount - when not PARTIAL but saved paid > 0 (View/Edit) -->
                <mat-form-field appearance="outline" class="w-full" *ngIf="billingForm.get('modeOfPayment')?.value !== 'PARTIAL' && ((billingForm.get('paymentStatus')?.value === 'PARTIAL') || (billingForm.get('paidAmount')?.value > 0))">
                  <mat-label>Paid Amount</mat-label>
                  <input matInput type="number" formControlName="paidAmount" 
                         (change)="onPaidAmountChange()" step="0.01" min="0" [disabled]="isView">
                  <mat-hint>Balance: ₹{{ calculateBalanceAmount() | number:'1.2-2' }}</mat-hint>
                </mat-form-field>
                <!-- View mode: show saved payment summary when PARTIAL or PAID (persisted data) -->
                <div class="payment-summary-view mt-2 col-span-2" *ngIf="isView && (billingForm.get('paymentStatus')?.value === 'PARTIAL' || billingForm.get('paymentStatus')?.value === 'PAID')">
                  <span class="text-sm text-gray-700">Paid: ₹{{ (billingForm.get('paidAmount')?.value ?? 0) | number:'1.2-2' }}</span>
                  <span class="text-sm text-gray-700 ml-4" *ngIf="(billingForm.get('receivedCashAmount')?.value ?? 0) > 0">Cash: ₹{{ (billingForm.get('receivedCashAmount')?.value ?? 0) | number:'1.2-2' }}</span>
                  <span class="text-sm text-gray-700 ml-4" *ngIf="(billingForm.get('receivedOnlineAmount')?.value ?? 0) > 0">Online: ₹{{ (billingForm.get('receivedOnlineAmount')?.value ?? 0) | number:'1.2-2' }}</span>
                  <span class="text-sm text-gray-700 ml-4" *ngIf="billingForm.get('paymentStatus')?.value === 'PARTIAL'">Balance: ₹{{ (billingForm.get('balanceAmount')?.value ?? 0) | number:'1.2-2' }}</span>
                </div>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Mode of Payment</mat-label>
                  <mat-select formControlName="modeOfPayment" [disabled]="isView" (selectionChange)="onModeOfPaymentChange()">
                    <mat-option value="CASH">Cash</mat-option>
                    <mat-option value="ONLINE">Online</mat-option>
                    <mat-option value="CHEQUE">Cheque</mat-option>
                    <mat-option value="PARTIAL">Partial</mat-option>
                  </mat-select>
                </mat-form-field>

                <!-- Online Payment Method - Show when mode is ONLINE -->
                <mat-form-field appearance="outline" class="w-full" *ngIf="billingForm.get('modeOfPayment')?.value === 'ONLINE'">
                  <mat-label>Payment Method</mat-label>
                  <mat-select formControlName="onlinePaymentMethod" [disabled]="isView">
                    <mat-option value="GPAY">GPay</mat-option>
                    <mat-option value="PHONEPE">PhonePe</mat-option>
                    <mat-option value="PAYTM">Paytm</mat-option>
                    <mat-option value="OTHER_UPI">Other UPI</mat-option>
                    <mat-option value="OTHERS">Others</mat-option>
                  </mat-select>
                </mat-form-field>

                <!-- Online Payment Reference - Show when mode is ONLINE (optional) -->
                <mat-form-field appearance="outline" class="w-full" *ngIf="billingForm.get('modeOfPayment')?.value === 'ONLINE'">
                  <mat-label>UPI ID / Phone Number (Optional)</mat-label>
                  <input matInput formControlName="onlinePaymentReference" 
                         placeholder="Enter UPI ID or Phone Number"
                         [disabled]="isView">
                  <mat-hint>For reference tracking (not mandatory)</mat-hint>
                </mat-form-field>
              </div>

              <!-- Display Invoice Date (for view mode) -->
              <div class="mt-2 text-sm text-gray-600" *ngIf="isView">
                <span>Invoice Date: {{ getFormattedDate() || 'Not available' }}</span>
              </div>
            </div>

            <!-- Shipping Address Section - Only for GST Bills -->
            <div class="form-section" *ngIf="billingForm.get('billType')?.value === 'GST'">
              <h3 class="section-title">Shipping Address</h3>
              <mat-checkbox formControlName="sameAsPartyAddress" (change)="onSameAsPartyAddressChange()" [disabled]="isView">
                Same as Party Address
              </mat-checkbox>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                <mat-form-field appearance="outline" class="w-full md:col-span-2">
                  <mat-label>Shipping Address</mat-label>
                  <textarea matInput formControlName="shippingAddress" rows="2" 
                            [disabled]="isView || billingForm.get('sameAsPartyAddress')?.value"
                            placeholder="Enter shipping address"></textarea>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>City</mat-label>
                  <input matInput formControlName="shippingCity" 
                         [disabled]="isView || billingForm.get('sameAsPartyAddress')?.value">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>State</mat-label>
                  <input matInput formControlName="shippingState" 
                         [disabled]="isView || billingForm.get('sameAsPartyAddress')?.value">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Pincode</mat-label>
                  <input matInput formControlName="shippingPincode" 
                         [disabled]="isView || billingForm.get('sameAsPartyAddress')?.value">
                </mat-form-field>
              </div>
            </div>

            <!-- Items Section -->
            <div class="form-section">
              <div class="flex justify-between items-center mb-4">
                <h3 class="section-title">Items</h3>
                <button type="button" mat-button (click)="addItem()" class="btn-secondary">
                  <mat-icon>add</mat-icon>
                  <span>Add Item</span>
                </button>
              </div>
              
              <div formArrayName="items" class="space-y-4">
                <div *ngFor="let item of itemsArray.controls; let i = index" 
                     [formGroupName]="i" 
                     class="item-row">
                  <div class="item-fields-row">
                    <mat-form-field appearance="outline" class="field-en-code">
                      <mat-label>EN Code</mat-label>
                      <input matInput formControlName="enCode"
                             (blur)="onEnCodeBlur(i)"
                             placeholder="Code">
                    </mat-form-field>
                    <mat-form-field appearance="outline" class="field-item">
                      <mat-label>Item</mat-label>
                      <input matInput formControlName="itemSearch"
                             [matAutocomplete]="itemAuto"
                             (input)="searchItem($event, i)"
                             placeholder="Search item...">
                      <mat-autocomplete #itemAuto="matAutocomplete" (optionSelected)="onItemSelected($event, i)">
                        <mat-option *ngFor="let item of getFilteredItems(i)" [value]="item">
                          <div class="autocomplete-option">
                            <span>{{ item.itemName }}</span>
                            <small>{{ item.itemCode }} - ₹{{ item.sellingPrice }}</small>
                          </div>
                        </mat-option>
                        <mat-option *ngIf="shouldShowCreateItem(i)" [value]="'__CREATE__'" class="create-option">
                          <mat-icon>add_circle</mat-icon>
                          <span>Create new item{{ itemSearchTerms.get(i) ? ' "' + itemSearchTerms.get(i) + '"' : '' }}</span>
                        </mat-option>
                      </mat-autocomplete>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="field-qty">
                      <mat-label>Qty</mat-label>
                      <input matInput type="number" formControlName="quantity" 
                             (change)="calculateItemTotal(i)" step="1" min="1"
                             (keydown)="preventDecimalInput($event)">
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="field-price">
                      <mat-label>Unit Price</mat-label>
                      <input matInput type="number" formControlName="unitPrice" 
                             (change)="calculateItemTotal(i)" step="0.01"
                             [disabled]="isView">
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="field-stock">
                      <mat-label>Stock</mat-label>
                      <input matInput [value]="getItemStock(i)" readonly>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="field-total">
                      <mat-label>Total Amount</mat-label>
                      <input matInput type="number" formControlName="totalAmount" readonly>
                    </mat-form-field>

                    <div class="field-action">
                      <button type="button" mat-icon-button (click)="removeItem(i)" class="btn-icon-danger">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <p *ngIf="itemsArray.length === 0" class="text-neutral-light text-sm text-center py-8">
                Click "Add Item" to add items to the bill
              </p>
            </div>

            <!-- Totals Section -->
            <div class="form-section bg-gray-50">
              <h3 class="section-title">Totals</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Discount (%)</mat-label>
                  <input matInput type="number" formControlName="discountPercent" 
                         (change)="onDiscountPercentChange()" step="0.01"
                         [disabled]="isView || discountAmountLocked">
                  <mat-hint *ngIf="discountAmountLocked">Enter discount amount instead</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Discount Amount (₹)</mat-label>
                  <input matInput type="number" formControlName="discountAmount" 
                         (change)="onDiscountAmountChange()" step="0.01"
                         [disabled]="isView || discountPercentLocked">
                  <mat-hint *ngIf="discountPercentLocked">Enter discount % instead</mat-hint>
                </mat-form-field>

                <div class="col-span-2 space-y-2">
                  <div class="flex justify-between text-gray-700">
                    <span>Subtotal:</span>
                    <span class="font-mono">₹{{ totals.subtotal.toFixed(2) }}</span>
                  </div>
                  <div class="flex justify-between text-gray-700">
                    <span>Discount:</span>
                    <span class="font-mono">₹{{ totals.discount.toFixed(2) }}
                      <span *ngIf="totals.discountPercent > 0">({{ totals.discountPercent.toFixed(2) }}%)</span>
                    </span>
                  </div>
                  <div *ngIf="billingForm.get('billType')?.value === 'GST'">
                    <div class="flex justify-between text-gray-700">
                      <span>CGST:</span>
                      <span class="font-mono">₹{{ totals.cgst.toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between text-gray-700">
                      <span>SGST:</span>
                      <span class="font-mono">₹{{ totals.sgst.toFixed(2) }}</span>
                    </div>
                  </div>
                  <div class="flex justify-between text-lg font-bold text-gray-900 border-t pt-2">
                    <span>Items Total Amount:</span>
                    <span class="font-mono" style="min-width: 150px; text-align: right;">₹{{ totals.total.toFixed(2) }}</span>
                  </div>
                  
                  <!-- Partial Payment Breakdown - Show when PARTIAL mode -->
                  <div *ngIf="billingForm.get('modeOfPayment')?.value === 'PARTIAL'" class="payment-breakdown mt-3 pt-3 border-t border-dashed">
                    <div class="flex justify-between text-amber-600 font-medium">
                      <span>Cash Received:</span>
                      <span class="font-mono">₹{{ (billingForm.get('receivedCashAmount')?.value || 0).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between text-blue-600 font-medium">
                      <span>Online Received:</span>
                      <span class="font-mono">₹{{ (billingForm.get('receivedOnlineAmount')?.value || 0).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between text-green-600 font-medium">
                      <span>Total Received:</span>
                      <span class="font-mono">₹{{ ((billingForm.get('receivedCashAmount')?.value || 0) + (billingForm.get('receivedOnlineAmount')?.value || 0)).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between text-orange-600 font-medium">
                      <span>Balance Due:</span>
                      <span class="font-mono">₹{{ calculateBalanceAmount().toFixed(2) }}</span>
                    </div>
                  </div>
                  <div *ngIf="billingForm.get('paymentStatus')?.value === 'PARTIAL' && billingForm.get('modeOfPayment')?.value !== 'PARTIAL'" class="payment-breakdown mt-3 pt-3 border-t border-dashed">
                    <div class="flex justify-between text-green-600 font-medium">
                      <span>Received Amount:</span>
                      <span class="font-mono">₹{{ (billingForm.get('paidAmount')?.value || 0).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between text-orange-600 font-medium">
                      <span>Balance Due:</span>
                      <span class="font-mono">₹{{ calculateBalanceAmount().toFixed(2) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Notes Section -->
            <div class="form-section">
              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Notes</mat-label>
                <textarea matInput formControlName="notes" rows="3"></textarea>
              </mat-form-field>
            </div>

            <!-- Actions -->
            <div class="flex gap-4 pt-4">
              <button *ngIf="!isView" mat-raised-button color="accent" type="button" 
                      (click)="saveAsDraft()" 
                      [disabled]="billingForm.get('partyId')?.invalid || loading || itemsArray.length === 0"
                      class="btn-secondary">
                <mat-icon>save</mat-icon>
                <span>Save as Draft</span>
              </button>
              <button *ngIf="!isView" mat-raised-button color="primary" type="submit" 
                      [disabled]="billingForm.invalid || loading || itemsArray.length === 0"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">{{ isEdit ? 'Update' : 'Add' }} Transaction</span>
                <span *ngIf="loading">Processing...</span>
              </button>
              <button *ngIf="!isView" mat-raised-button color="accent" type="button" 
                      (click)="saveAndPrint()" 
                      [disabled]="billingForm.invalid || loading"
                      class="btn-secondary">
                <mat-icon>print</mat-icon>
                <span>Save & Print</span>
              </button>
              <button *ngIf="isEdit && !isView && canReturnBill()" 
                      mat-raised-button color="warn" type="button" 
                      (click)="returnBill()" 
                      [disabled]="loading"
                      class="btn-return">
                <mat-icon>undo</mat-icon>
                <span>Return Transaction</span>
              </button>
              <button *ngIf="isView && billId" mat-raised-button color="primary" type="button" 
                      (click)="editBill()" class="btn-primary">
                Edit Bill
              </button>
              <button *ngIf="billId" mat-raised-button color="accent" type="button" 
                      (click)="printInvoice()" class="btn-secondary">
                <mat-icon>print</mat-icon>
                <span>Print</span>
              </button>
              <button *ngIf="billId" mat-raised-button color="accent" type="button" 
                      (click)="downloadInvoice()" class="btn-secondary">
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
    .page-container {
      animation: fadeSlideUp 0.3s ease-out;
    }

    .form-section {
      margin-bottom: 32px;
      padding: 24px;
      background: #F9FAFB;
      border-radius: 8px;
    }

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #1A1D2E;
      margin-bottom: 16px;
    }

    .bill-type-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      border: 2px solid #E5E7EB;
      border-radius: 8px;
      transition: all 0.2s;
    }

    .bill-type-toggle-group {
      display: flex;
      gap: 20px;
      flex-wrap: wrap;
      justify-content: center;
    }

    .bill-type-toggle-btn {
      flex: 1;
      min-width: 240px;
      max-width: 320px;
      padding: 20px 24px;
      border-radius: 12px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      align-items: center;
      gap: 16px;
      position: relative;
      border: 2px solid #E5E7EB;
      background: white;
      cursor: pointer;
    }

    .bill-type-toggle-btn:hover:not([disabled]) {
      border-color: #9CA3AF;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .bill-type-toggle-btn[disabled] {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .bill-type-toggle-btn.selected {
      border-color: #5B6FE8;
      background: linear-gradient(135deg, #F0F3FF 0%, #E8EDFF 100%);
      box-shadow: 0 4px 16px rgba(91, 111, 232, 0.25);
      transform: translateY(-2px);
    }

    .bill-type-toggle-btn.not-selected {
      border-color: #E5E7EB;
      background: #FFFFFF;
    }

    .bill-type-toggle-btn .btn-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: #5B6FE8;
    }

    .bill-type-toggle-btn.selected .btn-icon {
      color: #5B6FE8;
    }

    .bill-type-toggle-btn.not-selected .btn-icon {
      color: #9CA3AF;
    }

    .bill-type-toggle-btn .btn-content {
      flex: 1;
      text-align: left;
    }

    .bill-type-toggle-btn .btn-title {
      font-size: 16px;
      font-weight: 600;
      color: #1A1D2E;
      margin-bottom: 4px;
      line-height: 1.2;
    }

    .bill-type-toggle-btn.selected .btn-title {
      color: #1A1D2E;
    }

    .bill-type-toggle-btn.not-selected .btn-title {
      color: #6B7280;
    }

    .bill-type-toggle-btn .btn-subtitle {
      font-size: 12px;
      color: #6B7280;
      line-height: 1.4;
    }

    .bill-type-toggle-btn.selected .btn-subtitle {
      color: #4B5563;
      font-weight: 500;
    }

    .bill-type-toggle-btn .check-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
      color: #10B981;
      position: absolute;
      top: 12px;
      right: 12px;
    }

    .estimate-btn.selected {
      border-color: #8B5CF6;
      background: linear-gradient(135deg, #F5F3FF 0%, #EDE9FE 100%);
    }

    .estimate-btn.selected .btn-icon {
      color: #8B5CF6;
    }

    .invoice-btn.selected {
      border-color: #5B6FE8;
      background: linear-gradient(135deg, #F0F3FF 0%, #E8EDFF 100%);
    }

    .invoice-btn.selected .btn-icon {
      color: #5B6FE8;
    }

    .item-row {
      padding: 16px;
      background: white;
      border-radius: 8px;
      border: 1px solid #E5E7EB;
    }

    .text-neutral-light {
      color: #6B7280;
    }

    .autocomplete-option {
      display: flex;
      flex-direction: column;
    }

    .autocomplete-option small {
      color: #6B7280;
      font-size: 11px;
    }

    .create-option {
      color: #5B6FE8;
      font-weight: 500;
    }

    .create-option mat-icon {
      margin-right: 8px;
      font-size: 18px;
    }

    .bill-type-section {
      padding-top: 16px;
      padding-bottom: 16px;
    }

    /* Item fields row - flexbox layout for proper sizing */
    .item-fields-row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: flex-start;
    }

    .item-fields-row mat-form-field {
      flex-shrink: 0;
    }

    /* Field sizing - ensure text is fully visible */
    .field-en-code {
      width: 90px;
      min-width: 90px;
    }

    .field-item {
      flex: 1;
      min-width: 180px;
    }

    .field-qty {
      width: 80px;
      min-width: 80px;
    }

    .field-price {
      width: 120px;
      min-width: 120px;
    }

    .field-stock {
      width: 80px;
      min-width: 80px;
    }

    .field-total {
      width: 150px;
      min-width: 150px;
    }

    .field-action {
      width: 48px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-top: 8px;
    }

    /* Ensure inputs show full content - no ellipsis */
    .item-fields-row input {
      text-overflow: clip !important;
      overflow: visible !important;
    }

    /* Number inputs - right aligned */
    .field-qty input,
    .field-price input,
    .field-stock input,
    .field-total input {
      text-align: right;
      font-weight: 500;
    }

    .field-total input {
      font-weight: 600;
      font-size: 14px;
    }

    /* Override Material input infix width */
    ::ng-deep .item-fields-row .mat-mdc-form-field-infix {
      width: auto !important;
      min-width: unset !important;
    }

    /* Responsive: stack fields on smaller screens */
    @media (max-width: 1024px) {
      .item-fields-row {
        flex-wrap: wrap;
      }
      
      .field-item {
        min-width: 150px;
        flex: 1 1 150px;
      }
      
      .field-total {
        width: 130px;
        min-width: 130px;
      }
    }

    @media (max-width: 768px) {
      .item-fields-row {
        gap: 8px;
      }
      
      .field-en-code {
        width: 70px;
        min-width: 70px;
      }
      
      .field-qty,
      .field-stock {
        width: 70px;
        min-width: 70px;
      }
      
      .field-price,
      .field-total {
        width: 100px;
        min-width: 100px;
      }
    }
  `]
})
export class BillingFormComponent implements OnInit, AfterViewInit {
  billingForm: FormGroup;
  isEdit = false;
  isView = false;
  billId: string | null = null;
  loading = false;
  parties: Party[] = [];
  filteredParties: Party[] = [];
  availableItems: Item[] = [];
  filteredItems: Map<number, Item[]> = new Map();
  showCreateParty = false;
  showCreateItem: Map<number, boolean> = new Map();
  partySearchTerm = '';
  itemSearchTerms: Map<number, string> = new Map();
  private partySearch$ = new Subject<string>();
  private itemSearch$ = new Subject<{term: string, index: number}>();
  totals = {
    subtotal: 0,
    discount: 0,
    discountPercent: 0,
    cgst: 0,
    sgst: 0,
    total: 0
  };
  originalBill: any = null; // Store original bill for comparison
  hasManualEdits = false; // Track if user manually edited amounts
  invoiceNumber: string | null = null;
  discountAmountLocked = false;
  discountPercentLocked = false;
  billNumberError: string | null = null;
  private readonly BILLING_STATE_KEY = 'billing_form_state'; // Key for sessionStorage

  @ViewChild('pageContainer') pageContainer!: ElementRef;
  @ViewChild('card') card!: ElementRef;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private http: HttpClient,
    private router: Router,
    private route: ActivatedRoute,
    private animationService: AnimationService,
    private toastService: ToastService
  ) {
    this.billingForm = this.fb.group({
      invoiceNumber: [''], // Editable Bill No
      invoiceDate: [new Date(), Validators.required],
      partyId: ['', Validators.required],
      partySearch: [''],
      billType: ['ESTIMATE', Validators.required],
      // Shipping Address fields
      shippingAddress: [''],
      shippingCity: [''],
      shippingState: [''],
      shippingPincode: [''],
      sameAsPartyAddress: [false],
      items: this.fb.array([]),
      discountPercent: [0],
      discountAmount: [0],
      paymentStatus: ['PENDING'],
      paidAmount: [0],
      receivedCashAmount: [0], // For PARTIAL: cash received
      receivedOnlineAmount: [0], // For PARTIAL: online received
      balanceAmount: [0],
      modeOfPayment: ['CASH'],
      onlinePaymentMethod: [''], // GPay, PhonePe, Paytm, OtherUPI, Others
      onlinePaymentReference: [''], // UPI ID or Phone Number (optional)
      notes: [''],
      status: ['NORMAL'] // NORMAL, CORRECTED, RETURNED, or DRAFT
    });
    
    // Make paymentStatus required only for GST bills
    this.billingForm.get('billType')?.valueChanges.subscribe(billType => {
      const paymentStatusControl = this.billingForm.get('paymentStatus');
      if (billType === 'ESTIMATE') {
        paymentStatusControl?.clearValidators();
      } else {
        paymentStatusControl?.setValidators([Validators.required]);
      }
      paymentStatusControl?.updateValueAndValidity();
    });
  }

  get itemsArray(): FormArray {
    return this.billingForm.get('items') as FormArray;
  }

  ngOnInit() {
    this.loadParties();
    this.loadItems();
    this.setupSearchSubscriptions();
    
    // Check if editing/viewing existing bill FIRST before adding default item row
    const id = this.route.snapshot.paramMap.get('id');
    const url = this.router.url;
    
    if (id) {
      this.billId = id;
      // Check if we're in view mode (URL contains '/view/')
      if (url.includes('/view/')) {
        this.isView = true;
        // Disable form in view mode
        this.billingForm.disable();
      } else {
        this.isEdit = true;
      }
      this.loadBill(id);
    } else {
      // Add default item row ONLY when creating new bill (not editing)
      this.addItem();
    }
    
    // Check for itemId query param (returning from item creation)
    this.route.queryParams.subscribe(params => {
      if (params['itemId'] && params['itemIndex'] !== undefined) {
        const itemIndex = parseInt(params['itemIndex'], 10);
        const itemId = params['itemId'];
        
        // Try to restore billing state from sessionStorage
        const stateRestored = this.restoreBillingState();
        
        // If state not restored and no items yet, add default item row
        if (!stateRestored && this.itemsArray.length === 0 && !this.isEdit) {
          this.addItem();
        }
        
        // Wait for items to load, then add the newly created item
        const addNewItem = () => {
          // Refresh items list to include newly created item
          this.apiService.getPaged<Item>('/items', 0, 1000).subscribe({
            next: (response) => {
              this.availableItems = response.content;
              this.preselectItem(itemId, itemIndex);
              // Clear saved state after successful restoration
              this.clearBillingState();
            },
            error: () => {
              this.preselectItem(itemId, itemIndex);
              this.clearBillingState();
            }
          });
        };
        
        if (this.availableItems.length > 0) {
          addNewItem();
        } else {
          // If items not loaded yet, wait for them
          const checkItems = setInterval(() => {
            if (this.availableItems.length > 0) {
              addNewItem();
              clearInterval(checkItems);
            }
          }, 100);
          
          // Timeout after 5 seconds
          setTimeout(() => {
            clearInterval(checkItems);
            this.clearBillingState();
          }, 5000);
        }
        
        // Clean up query params
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { itemId: null, itemIndex: null },
          queryParamsHandling: 'merge'
        });
      }
    });
  }
  
  getFormattedDate(): string {
    const dateValue = this.billingForm.get('invoiceDate')?.value;
    if (dateValue) {
      const date = new Date(dateValue);
      return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      });
    }
    return '';
  }
  
  onPaymentStatusChange() {
    const paymentStatus = this.billingForm.get('paymentStatus')?.value;
    const modeOfPayment = this.billingForm.get('modeOfPayment')?.value;
    const totalAmount = this.getTotalAmount();
    
    if (paymentStatus === 'PAID') {
      // Set paid amount to total amount and derive cash/online based on mode
      if (modeOfPayment === 'CASH') {
        this.billingForm.patchValue({ 
          paidAmount: totalAmount, 
          receivedCashAmount: totalAmount,
          receivedOnlineAmount: 0,
          balanceAmount: 0 
        });
      } else if (modeOfPayment === 'ONLINE' || modeOfPayment === 'CHEQUE') {
        this.billingForm.patchValue({ 
          paidAmount: totalAmount, 
          receivedCashAmount: 0,
          receivedOnlineAmount: totalAmount,
          balanceAmount: 0 
        });
      } else {
        // PARTIAL mode with PAID status - keep existing split or default to online
        const existingCash = Number(this.billingForm.get('receivedCashAmount')?.value || 0);
        const existingOnline = Number(this.billingForm.get('receivedOnlineAmount')?.value || 0);
        if (existingCash + existingOnline < totalAmount) {
          // If split doesn't cover total, default to online
          this.billingForm.patchValue({ 
            paidAmount: totalAmount, 
            receivedCashAmount: existingCash,
            receivedOnlineAmount: totalAmount - existingCash,
            balanceAmount: 0 
          });
        } else {
          this.billingForm.patchValue({ paidAmount: totalAmount, balanceAmount: 0 });
        }
      }
    } else if (paymentStatus === 'PENDING') {
      // Reset all payment amounts
      this.billingForm.patchValue({ 
        paidAmount: 0, 
        receivedCashAmount: 0,
        receivedOnlineAmount: 0,
        balanceAmount: totalAmount 
      });
    }
    // For PARTIAL status, let user enter the amounts via onPartialPaymentChange
  }
  
  onPartialPaymentChange() {
    const totalAmount = this.getTotalAmount();
    let cash = Number(this.billingForm.get('receivedCashAmount')?.value || 0);
    let online = Number(this.billingForm.get('receivedOnlineAmount')?.value || 0);
    if (cash + online > totalAmount) {
      this.toastService.error('Validation Error', 'Cash + Online cannot exceed total bill amount.');
      if (cash > totalAmount) {
        cash = totalAmount;
        online = 0;
        this.billingForm.patchValue({ receivedCashAmount: cash, receivedOnlineAmount: 0 }, { emitEvent: false });
      } else {
        online = Math.max(0, totalAmount - cash);
        this.billingForm.patchValue({ receivedOnlineAmount: online }, { emitEvent: false });
      }
    }
    const paidAmount = cash + online;
    const balanceAmount = Math.max(0, totalAmount - paidAmount);
    this.billingForm.patchValue({ paidAmount, balanceAmount }, { emitEvent: false });
    if (paidAmount <= 0) {
      this.billingForm.patchValue({ paymentStatus: 'PENDING' });
    } else if (paidAmount >= totalAmount) {
      this.billingForm.patchValue({ paymentStatus: 'PAID', balanceAmount: 0 });
    } else {
      this.billingForm.patchValue({ paymentStatus: 'PARTIAL' });
    }
  }
  
  onPaidAmountChange() {
    const paidAmount = this.billingForm.get('paidAmount')?.value || 0;
    const totalAmount = this.getTotalAmount();
    
    if (paidAmount > totalAmount) {
      this.toastService.error('Validation Error', 'Received amount cannot exceed total bill amount.');
      this.billingForm.patchValue({ paidAmount: totalAmount }, { emitEvent: false });
      return;
    }
    
    const balanceAmount = Math.max(0, totalAmount - paidAmount);
    this.billingForm.patchValue({ balanceAmount: balanceAmount }, { emitEvent: false });
    
    if (paidAmount <= 0) {
      this.billingForm.patchValue({ paymentStatus: 'PENDING' });
    } else if (paidAmount >= totalAmount) {
      this.billingForm.patchValue({ paymentStatus: 'PAID', paidAmount: totalAmount, balanceAmount: 0 });
    } else {
      this.billingForm.patchValue({ paymentStatus: 'PARTIAL' });
    }
  }
  
  validatePartialPayment(): boolean {
    const totalAmount = this.getTotalAmount();
    const modeOfPayment = this.billingForm.get('modeOfPayment')?.value;
    if (modeOfPayment === 'PARTIAL') {
      const cash = Number(this.billingForm.get('receivedCashAmount')?.value || 0);
      const online = Number(this.billingForm.get('receivedOnlineAmount')?.value || 0);
      if (cash + online > totalAmount) {
        this.toastService.error('Validation Error', 'Cash + Online cannot exceed total bill amount.');
        return false;
      }
      return true;
    }
    const paidAmount = this.billingForm.get('paidAmount')?.value || 0;
    if (paidAmount > totalAmount) {
      this.toastService.error('Validation Error', 'Received amount cannot exceed total bill amount.');
      return false;
    }
    return true;
  }
  
  calculateBalanceAmount(): number {
    const totalAmount = this.getTotalAmount();
    const modeOfPayment = this.billingForm.get('modeOfPayment')?.value;
    const paidAmount = modeOfPayment === 'PARTIAL'
      ? (Number(this.billingForm.get('receivedCashAmount')?.value || 0) + Number(this.billingForm.get('receivedOnlineAmount')?.value || 0))
      : (this.billingForm.get('paidAmount')?.value || 0);
    return Math.max(0, totalAmount - paidAmount);
  }
  
  /** Returns the current invoice total (from totals or 0). */
  getTotalAmount(): number {
    return this.totals?.total ?? 0;
  }
  
  onModeOfPaymentChange() {
    const modeOfPayment = this.billingForm.get('modeOfPayment')?.value;
    const paymentStatus = this.billingForm.get('paymentStatus')?.value;
    const paidAmount = Number(this.billingForm.get('paidAmount')?.value || 0);
    
    // Clear online payment fields if not ONLINE
    if (modeOfPayment !== 'ONLINE') {
      this.billingForm.patchValue({
        onlinePaymentMethod: '',
        onlinePaymentReference: ''
      });
    }
    
    if (modeOfPayment === 'PARTIAL') {
      this.billingForm.patchValue({ paymentStatus: 'PARTIAL', receivedCashAmount: 0, receivedOnlineAmount: 0 });
      this.onPartialPaymentChange();
    } else if (paymentStatus === 'PAID' || paidAmount > 0) {
      // Update cash/online split based on new mode when already paid
      const totalAmount = this.getTotalAmount();
      const amountToUse = paymentStatus === 'PAID' ? totalAmount : paidAmount;
      
      if (modeOfPayment === 'CASH') {
        this.billingForm.patchValue({ 
          receivedCashAmount: amountToUse,
          receivedOnlineAmount: 0
        });
      } else if (modeOfPayment === 'ONLINE' || modeOfPayment === 'CHEQUE') {
        this.billingForm.patchValue({ 
          receivedCashAmount: 0,
          receivedOnlineAmount: amountToUse
        });
      }
    }
  }
  
  onSameAsPartyAddressChange() {
    if (this.billingForm.get('sameAsPartyAddress')?.value) {
      // Copy party address to shipping address
      const partyId = this.billingForm.get('partyId')?.value;
      const party = this.parties.find(p => p.id === partyId);
      if (party) {
        this.billingForm.patchValue({
          shippingAddress: party.address || '',
          shippingCity: party.city || '',
          shippingState: party.state || '',
          shippingPincode: party.pincode || ''
        });
      }
    }
  }
  
  getCurrentTime(): string {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
  }
  
  validateBillNumber() {
    const billNo = this.billingForm.get('invoiceNumber')?.value?.trim();
    if (!billNo || billNo === '') {
      this.billNumberError = null;
      this.billingForm.get('invoiceNumber')?.setErrors(null);
      return;
    }
    
    // Validation will be done on save - this is just for UI feedback
    // The actual validation happens server-side
    this.billNumberError = null;
  }
  
  preselectItem(itemId: string, itemIndex: number) {
    const item = this.availableItems.find(i => i.id === itemId);
    if (!item) {
      // Item not found in available items, try to fetch it
      this.apiService.get<Item>(`/items/${itemId}`).subscribe({
        next: (fetchedItem) => {
          this.availableItems.push(fetchedItem);
          this.selectItemInRow(fetchedItem, itemIndex);
        },
        error: () => {
          this.toastService.error('Error', 'Failed to load created item');
        }
      });
      return;
    }
    
    this.selectItemInRow(item, itemIndex);
  }
  
  selectItemInRow(item: Item, itemIndex: number) {
    // Ensure we have enough item rows
    while (this.itemsArray.length <= itemIndex) {
      this.addItem();
    }
    
    const itemGroup = this.itemsArray.at(itemIndex) as FormGroup;
    itemGroup.patchValue({
      itemId: item.id,
      itemSearch: item.itemName
    });
    this.onItemChange(itemIndex);
    this.toastService.success('Success', `Item "${item.itemName}" added to bill`);
  }
  
  // Save billing form state to sessionStorage before navigating to item creation
  saveBillingState(pendingItemIndex: number) {
    const state = {
      formValue: this.billingForm.getRawValue(),
      partySearchTerm: this.partySearchTerm,
      totals: this.totals,
      pendingItemIndex: pendingItemIndex,
      billId: this.billId,
      isEdit: this.isEdit,
      timestamp: Date.now()
    };
    sessionStorage.setItem(this.BILLING_STATE_KEY, JSON.stringify(state));
  }
  
  // Restore billing form state from sessionStorage
  restoreBillingState(): boolean {
    const stateJson = sessionStorage.getItem(this.BILLING_STATE_KEY);
    if (!stateJson) return false;
    
    try {
      const state = JSON.parse(stateJson);
      
      // Check if state is recent (within 10 minutes)
      if (Date.now() - state.timestamp > 10 * 60 * 1000) {
        sessionStorage.removeItem(this.BILLING_STATE_KEY);
        return false;
      }
      
      // Restore form values
      if (state.formValue) {
        // Clear existing items first
        while (this.itemsArray.length > 0) {
          this.itemsArray.removeAt(0);
        }
        
        // Restore form values (except items)
        const { items, ...formWithoutItems } = state.formValue;
        this.billingForm.patchValue(formWithoutItems);
        
        // Restore items
        if (items && Array.isArray(items)) {
          items.forEach((item: any) => {
            const itemGroup = this.fb.group({
              itemId: [item.itemId || '', Validators.required],
              enCode: [item.enCode || ''],
              itemSearch: [item.itemSearch || ''],
              quantity: [item.quantity || 1, [Validators.required, Validators.min(1)]],
              unitPrice: [item.unitPrice || 0, Validators.required],
              totalAmount: [item.totalAmount || 0]
            });
            this.itemsArray.push(itemGroup);
          });
        }
      }
      
      // Restore other state
      this.partySearchTerm = state.partySearchTerm || '';
      this.totals = state.totals || this.totals;
      this.billId = state.billId;
      this.isEdit = state.isEdit;
      
      return true;
    } catch (e) {
      console.error('Error restoring billing state:', e);
      sessionStorage.removeItem(this.BILLING_STATE_KEY);
      return false;
    }
  }
  
  // Clear saved billing state
  clearBillingState() {
    sessionStorage.removeItem(this.BILLING_STATE_KEY);
  }

  setupSearchSubscriptions() {
    this.partySearch$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(term => {
      this.partySearchTerm = term;
      if (term.length >= 2) {
        this.apiService.search<Party>('/clients', term, 0, 10).subscribe({
          next: (response) => {
            this.filteredParties = response.content;
            this.showCreateParty = this.filteredParties.length === 0;
          },
          error: () => {
            this.filteredParties = [];
            this.showCreateParty = term.length > 0;
          }
        });
      } else {
        this.filteredParties = this.parties.slice(0, 10);
        this.showCreateParty = false;
      }
    });

    this.itemSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged((prev, curr) => prev.term === curr.term && prev.index === curr.index)
    ).subscribe(({term, index}) => {
      this.itemSearchTerms.set(index, term);
      if (term.length >= 2) {
        this.apiService.search<Item>('/items', term, 0, 10).subscribe({
          next: (response) => {
            this.filteredItems.set(index, response.content);
            this.showCreateItem.set(index, response.content.length === 0);
          },
          error: () => {
            this.filteredItems.set(index, []);
            this.showCreateItem.set(index, term.length > 0);
          }
        });
      } else {
        this.filteredItems.set(index, this.availableItems.slice(0, 10));
        this.showCreateItem.set(index, false);
      }
    });
  }

  ngAfterViewInit() {
    setTimeout(() => {
      if (this.pageContainer?.nativeElement) {
        this.animationService.fadeIn(this.pageContainer.nativeElement);
      }
      if (this.card?.nativeElement) {
        this.animationService.scaleIn(this.card.nativeElement, 0.95);
      }
    }, 100);
  }

  loadParties() {
    // Load only CUSTOMER parties for Estimate/Billing
    this.apiService.get<Party[]>('/parties/type/CUSTOMER').subscribe({
      next: (parties) => {
        this.parties = parties;
        this.filteredParties = this.parties.slice(0, 10);
      },
      error: () => {
        // Fallback to all parties if type filter fails
        this.apiService.getPaged<Party>('/clients', 0, 100).subscribe({
          next: (response) => {
            this.parties = response.content.filter((p: Party) => !p.partyType || p.partyType === 'CUSTOMER');
            this.filteredParties = this.parties.slice(0, 10);
          },
          error: () => {
            this.toastService.error('Error', 'Failed to load parties');
          }
        });
      }
    });
  }

  loadItems() {
    this.apiService.getPaged<Item>('/items', 0, 1000).subscribe({
      next: (response) => {
        this.availableItems = response.content;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load items');
      }
    });
  }

  loadBill(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/invoices/${id}`).subscribe({
      next: (bill) => {
        // Store original bill for comparison
        this.originalBill = JSON.parse(JSON.stringify(bill));
        this.hasManualEdits = false;
        
        this.invoiceNumber = bill.invoiceNumber;
        
        // Use invoiceDateTime if available, otherwise use invoiceDate
        let invoiceDateValue: Date;
        if (bill.invoiceDateTime) {
          invoiceDateValue = new Date(bill.invoiceDateTime);
        } else if (bill.invoiceDate) {
          invoiceDateValue = new Date(bill.invoiceDate);
        } else {
          invoiceDateValue = new Date();
        }
        
        // IMPORTANT: Always use partyName from the bill (stored at creation time)
        // Do NOT use party master data - party name should be immutable per transaction
        const partyName = bill.partyName || '';
        
        this.billingForm.patchValue({
          invoiceNumber: bill.invoiceNumber || '',
          invoiceDate: invoiceDateValue,
          partyId: bill.partyId,
          partySearch: partyName, // Always use stored partyName from bill, not from party master
          billType: bill.billType || 'ESTIMATE',
          discountPercent: bill.discountPercent || 0,
          discountAmount: bill.discountAmount || 0,
          paymentStatus: bill.paymentStatus || 'PENDING',
          paidAmount: bill.paidAmount != null ? Number(bill.paidAmount) : 0,
          receivedCashAmount: bill.receivedCashAmount != null ? Number(bill.receivedCashAmount) : 0,
          receivedOnlineAmount: bill.receivedOnlineAmount != null ? Number(bill.receivedOnlineAmount) : 0,
          balanceAmount: bill.balanceAmount != null ? Number(bill.balanceAmount) : 0,
          modeOfPayment: bill.modeOfPayment || 'CASH',
          onlinePaymentMethod: bill.onlinePaymentMethod || '',
          onlinePaymentReference: bill.onlinePaymentReference || '',
          shippingAddress: bill.shippingAddress || '',
          shippingCity: bill.shippingCity || '',
          shippingState: bill.shippingState || '',
          shippingPincode: bill.shippingPincode || '',
          sameAsPartyAddress: bill.sameAsPartyAddress || false,
          notes: bill.notes,
          status: bill.status || 'NORMAL'
        });
        
        // If invoice is returned, disable form and set to view mode
        if (bill.status === 'RETURNED') {
          this.billingForm.disable();
          this.isView = true;
        }
        
        // Set the party search term using stored partyName
        this.partySearchTerm = partyName;

        // Load items - use itemName from bill response for autocomplete display
        bill.items?.forEach((item: any) => {
          this.addItem(item);
        });

        this.calculateTotals();
        
        // Backfill balanceAmount when loading old records that don't have it (null/undefined)
        const balanceVal = this.billingForm.get('balanceAmount')?.value;
        if (balanceVal == null && (this.totals?.total ?? 0) > 0) {
          const paid = Number(this.billingForm.get('paidAmount')?.value || 0);
          this.billingForm.patchValue({ balanceAmount: Math.max(0, (this.totals?.total ?? 0) - paid) });
        }
        
        // Ensure form stays disabled in view mode
        if (this.isView) {
          this.billingForm.disable();
        }
        
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load bill');
        this.router.navigate(['/master/billing']);
      }
    });
  }

  addItem(itemData?: any) {
    const itemGroup = this.fb.group({
      itemId: [itemData?.itemId || '', Validators.required],
      enCode: [itemData?.enCode || ''],
      itemSearch: [''],
      quantity: [itemData?.quantity || 1, [Validators.required, Validators.min(1)]],
      unitPrice: [itemData?.unitPrice || 0, Validators.required],
      totalAmount: [itemData?.totalAmount || 0]
    });
    const index = this.itemsArray.length;
    this.itemsArray.push(itemGroup);
    this.filteredItems.set(index, this.availableItems.slice(0, 10));
    this.showCreateItem.set(index, false);
    
    // If loading existing item, set itemSearch and enCode for autocomplete display
    if (itemData?.itemId) {
      // Use itemName from bill data directly (most reliable)
      if (itemData.itemName) {
        itemGroup.patchValue({ itemSearch: itemData.itemName });
      } else {
        // Fallback: try to find in availableItems
        const item = this.availableItems.find(i => i.id === itemData.itemId);
        if (item) {
          itemGroup.patchValue({ 
            itemSearch: item.itemName,
            enCode: item.enCode || itemData.enCode || ''
          });
        }
      }
      // Load EN Code from item if not in itemData
      if (!itemData.enCode && itemData.itemId) {
        const item = this.availableItems.find(i => i.id === itemData.itemId);
        if (item && item.enCode) {
          itemGroup.patchValue({ enCode: item.enCode });
        }
      }
    }
    
    if (!itemData) {
      this.calculateTotals();
    }
  }

  removeItem(index: number) {
    this.itemsArray.removeAt(index);
    this.calculateTotals();
  }
  
  onEnCodeBlur(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const enCode = itemGroup.get('enCode')?.value?.trim();
    
    if (!enCode) return;
    
    // Search for item by EN Code
    this.apiService.get<Item>(`/items/en-code/${enCode}`).subscribe({
      next: (item) => {
        // Item found - auto-add it
        itemGroup.patchValue({
          itemId: item.id,
          itemSearch: item.itemName
        });
        this.onItemChange(index);
        this.toastService.success('Success', `Item "${item.itemName}" added via EN Code`);
      },
      error: (err) => {
        // EN Code not found - show message
        this.toastService.error('Error', `No item found with EN Code: ${enCode}`);
      }
    });
  }

  searchParty(event: Event) {
    if (this.isView) return; // Don't search in view mode
    const term = (event.target as HTMLInputElement).value;
    this.partySearch$.next(term);
  }

  onPartySelected(event: any) {
    if (this.isView) return; // Don't allow selection in view mode
    
    const value = event.option.value;
    if (value === '__CREATE__') {
      this.router.navigate(['/parties/create'], { 
        queryParams: { 
          name: this.partySearchTerm,
          returnUrl: this.router.url 
        } 
      });
    } else if (typeof value === 'object' && value.id) {
      this.billingForm.patchValue({
        partyId: value.id,
        partySearch: value.partyName
      });
      this.onPartyChange();
    }
  }

  onPartyChange() {
    // Future: auto-detect inter-state based on party state vs company state
    this.calculateTotals();
  }

  searchItem(event: Event, index: number) {
    if (this.isView) return; // Don't search in view mode
    const term = (event.target as HTMLInputElement).value;
    this.itemSearch$.next({ term, index });
  }

  onItemSelected(event: any, index: number) {
    if (this.isView) return; // Don't allow selection in view mode
    
    const value = event.option.value;
    if (value === '__CREATE__') {
      const searchTerm = this.itemSearchTerms.get(index) || '';
      
      // Save billing form state to sessionStorage before navigating
      this.saveBillingState(index);
      
      // Navigate to item creation in same tab
      this.router.navigate(['/master/items/create'], { 
        queryParams: { 
          name: searchTerm,
          returnUrl: this.router.url,
          itemIndex: index.toString()
        } 
      });
    } else if (typeof value === 'object' && value.id) {
      // Check if this item already exists in the items array
      const existingIndex = this.findExistingItemIndex(value.id);
      
      if (existingIndex !== -1 && existingIndex !== index) {
        // Item already exists in another row - merge quantities
        const existingItemGroup = this.itemsArray.at(existingIndex) as FormGroup;
        const currentItemGroup = this.itemsArray.at(index) as FormGroup;
        const existingQuantity = existingItemGroup.get('quantity')?.value || 1;
        const newQuantity = existingQuantity + 1; // Increment by 1 (default quantity)
        
        existingItemGroup.patchValue({
          quantity: newQuantity
        });
        this.calculateItemTotal(existingIndex);
        
        // Remove the duplicate row
        this.itemsArray.removeAt(index);
        
        // Update filtered items map indices
        this.updateFilteredItemsIndices();
        
        this.toastService.success('Item Merged', `Quantity updated to ${newQuantity}`);
      } else {
        // New item or same row - proceed normally
      const itemGroup = this.itemsArray.at(index) as FormGroup;
      itemGroup.patchValue({
        itemId: value.id,
        itemSearch: value.itemName
      });
      this.onItemChange(index);
    }
    }
  }
  
  private findExistingItemIndex(itemId: string): number {
    for (let i = 0; i < this.itemsArray.length; i++) {
      const itemGroup = this.itemsArray.at(i) as FormGroup;
      if (itemGroup.get('itemId')?.value === itemId) {
        return i;
      }
    }
    return -1;
  }
  
  private updateFilteredItemsIndices() {
    // Rebuild filtered items map with correct indices
    const newFilteredItems = new Map<number, Item[]>();
    const newShowCreateItem = new Map<number, boolean>();
    
    for (let i = 0; i < this.itemsArray.length; i++) {
      const existingFiltered = this.filteredItems.get(i);
      if (existingFiltered) {
        newFilteredItems.set(i, existingFiltered);
      } else {
        newFilteredItems.set(i, this.availableItems.slice(0, 10));
      }
      
      const existingShow = this.showCreateItem.get(i);
      if (existingShow !== undefined) {
        newShowCreateItem.set(i, existingShow);
      } else {
        newShowCreateItem.set(i, false);
      }
    }
    
    this.filteredItems = newFilteredItems;
    this.showCreateItem = newShowCreateItem;
  }

  getFilteredItems(index: number): Item[] {
    return this.filteredItems.get(index) || this.availableItems.slice(0, 10);
  }

  shouldShowCreateItem(index: number): boolean {
    return this.showCreateItem.get(index) || false;
  }

  onItemChange(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const itemId = itemGroup.get('itemId')?.value;
    const selectedItem = this.availableItems.find(i => i.id === itemId);
    
    if (selectedItem) {
      itemGroup.patchValue({
        unitPrice: selectedItem.sellingPrice,
        enCode: selectedItem.enCode || ''
      });
      this.calculateItemTotal(index);
      
      // Validate stock for both ESTIMATE and GST bills
      const quantity = itemGroup.get('quantity')?.value || 0;
      if (selectedItem.stockQuantity < quantity) {
        this.toastService.warning('Stock Warning', 
          `Insufficient stock for ${selectedItem.itemName}. Available: ${selectedItem.stockQuantity}`);
      }
    }
  }
  
  preventDecimalInput(event: KeyboardEvent) {
    if (event.key === '.' || event.key === ',' || event.key === 'e' || event.key === 'E' || event.key === '+' || event.key === '-') {
      event.preventDefault();
    }
  }
  
  getItemStock(index: number): number {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const itemId = itemGroup.get('itemId')?.value;
    const selectedItem = this.availableItems.find(i => i.id === itemId);
    return selectedItem ? selectedItem.stockQuantity : 0;
  }
  
  hasInsufficientStock(index: number): boolean {
    // Validate stock for both ESTIMATE and GST bills
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    const quantity = itemGroup.get('quantity')?.value || 0;
    const stock = this.getItemStock(index);
    return stock < quantity;
  }

  calculateItemTotal(index: number) {
    const itemGroup = this.itemsArray.at(index) as FormGroup;
    let quantity = itemGroup.get('quantity')?.value || 0;
    // Normalize quantity to integer
    quantity = Math.max(1, Math.round(quantity));
    itemGroup.patchValue({ quantity }, { emitEvent: false });
    
    const unitPrice = itemGroup.get('unitPrice')?.value || 0;
    const total = quantity * unitPrice;
    
    itemGroup.patchValue({ totalAmount: total }, { emitEvent: false });
    
    // Check if this is a manual edit (user changed rate/quantity)
    if (this.isEdit && this.originalBill) {
      const originalItem = this.originalBill.items?.[index];
      if (originalItem) {
        const rateChanged = Math.abs(originalItem.unitPrice - unitPrice) > 0.01;
        const qtyChanged = Math.abs(originalItem.quantity - quantity) > 0;
        if (rateChanged || qtyChanged) {
          this.hasManualEdits = true;
        }
      }
    }
    
    this.calculateTotals();
  }

  onDiscountPercentChange() {
    this.discountAmountLocked = false;
    this.discountPercentLocked = true;
    this.calculateTotals();
  }
  
  onDiscountAmountChange() {
    this.discountPercentLocked = false;
    this.discountAmountLocked = true;
    this.calculateTotals();
  }

  calculateTotals() {
    let subtotal = 0;
    
    // Calculate subtotal
    this.itemsArray.controls.forEach(control => {
      subtotal += control.get('totalAmount')?.value || 0;
    });

    // Calculate discount with auto-calculation
    let discount = 0;
    let discountPercent = 0;
    
    if (this.discountPercentLocked) {
      // If discount % is being edited, calculate amount
      discountPercent = this.billingForm.get('discountPercent')?.value || 0;
      discount = (subtotal * discountPercent) / 100;
      this.billingForm.patchValue({ discountAmount: discount }, { emitEvent: false });
    } else if (this.discountAmountLocked) {
      // If discount amount is being edited, calculate %
      discount = this.billingForm.get('discountAmount')?.value || 0;
      if (subtotal > 0) {
        discountPercent = (discount / subtotal) * 100;
      }
      this.billingForm.patchValue({ discountPercent: discountPercent }, { emitEvent: false });
    } else {
      // Default: use discount % if available, otherwise use amount
      discountPercent = this.billingForm.get('discountPercent')?.value || 0;
      if (discountPercent > 0) {
        discount = (subtotal * discountPercent) / 100;
        this.billingForm.patchValue({ discountAmount: discount }, { emitEvent: false });
      } else {
        discount = this.billingForm.get('discountAmount')?.value || 0;
        if (discount > 0 && subtotal > 0) {
          discountPercent = (discount / subtotal) * 100;
          this.billingForm.patchValue({ discountPercent: discountPercent }, { emitEvent: false });
        }
      }
    }
    
    const taxableAmount = subtotal - discount;

    // Calculate GST if bill type is GST (only CGST and SGST, IGST removed)
    let cgst = 0, sgst = 0;
    const billType = this.billingForm.get('billType')?.value;
    
    if (billType === 'GST' && taxableAmount > 0) {
      // Calculate GST from item tax rates
      // Calculate GST on each item's taxable portion (after proportional discount)
      let totalGst = 0;
      let hasItemsWithTaxRate = false;
      
      this.itemsArray.controls.forEach(control => {
        const itemId = control.get('itemId')?.value;
        const itemTotal = control.get('totalAmount')?.value || 0;
        
        if (itemId && itemTotal > 0) {
          const selectedItem = this.availableItems.find(i => i.id === itemId);
          let itemTaxRate = 0;
          
          if (selectedItem && selectedItem.taxRate != null && selectedItem.taxRate > 0) {
            itemTaxRate = selectedItem.taxRate;
            hasItemsWithTaxRate = true;
          } else {
            // If item doesn't have tax rate, use default 18% GST
            itemTaxRate = 18;
          }
          
          // Calculate this item's proportion of the subtotal
          const itemProportion = subtotal > 0 ? (itemTotal / subtotal) : 0;
          // Calculate this item's taxable amount (after proportional discount)
          const itemTaxableAmount = taxableAmount * itemProportion;
          // Calculate GST for this item
          const itemGst = (itemTaxableAmount * itemTaxRate) / 100;
          totalGst += itemGst;
        }
      });
      
      // If no items have tax rates set, apply default 18% GST on entire taxable amount
      // (This handles the case where items exist but none have taxRate property)
      if (!hasItemsWithTaxRate && this.itemsArray.length > 0 && totalGst === 0) {
        totalGst = (taxableAmount * 18) / 100;
      }
      
      // Split GST into CGST and SGST (50/50)
      cgst = totalGst / 2;
      sgst = totalGst / 2;
    }

    const total = taxableAmount + cgst + sgst;

    this.totals = {
      subtotal,
      discount,
      discountPercent,
      cgst,
      sgst,
      total
    };
  }

  onSubmit() {
    if (this.billingForm.valid) {
      // Validate partial payment before submitting
      if (!this.validatePartialPayment()) {
        return;
      }
      
      this.loading = true;
      const formValue = this.billingForm.value;
      
      // Determine status: CORRECTED if manual edits detected, otherwise NORMAL
      let status = 'NORMAL';
      if (this.isEdit) {
        // Check if total amount changed significantly
        if (this.originalBill) {
          const totalChanged = Math.abs(this.originalBill.totalAmount - this.totals.total) > 0.01;
          if (this.hasManualEdits || totalChanged) {
            status = 'CORRECTED';
          }
        }
      }
      
      // REMOVED: Stock validation that blocks billing
      // Now allowing negative stock - show warning but allow save
      for (let i = 0; i < formValue.items.length; i++) {
        const item = formValue.items[i];
        const selectedItem = this.availableItems.find(it => it.id === item.itemId);
        if (selectedItem && selectedItem.stockQuantity < item.quantity) {
          this.toastService.warning('Stock Warning', 
            `Insufficient stock for ${selectedItem.itemName}. Available: ${selectedItem.stockQuantity}, Required: ${item.quantity}. Bill will be created with negative stock.`);
        }
      }
      
      // Combine date and current time for invoiceDateTime
      // Format date-time manually to preserve local time (avoid UTC conversion)
      const dateObj = new Date(formValue.invoiceDate);
      const now = new Date();
      const hours = now.getHours();
      const minutes = now.getMinutes();
      
      // Format date as YYYY-MM-DD
      const year = dateObj.getFullYear();
      const month = String(dateObj.getMonth() + 1).padStart(2, '0');
      const day = String(dateObj.getDate()).padStart(2, '0');
      const invoiceDateStr = `${year}-${month}-${day}`;
      
      // Format date-time as YYYY-MM-DDTHH:mm:ss (local time, no timezone)
      const invoiceDateTimeStr = `${invoiceDateStr}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
      
      const bill = {
        invoiceNumber: formValue.invoiceNumber?.trim() || null, // Empty string becomes null for auto-generation
        invoiceDate: invoiceDateStr, // Date only (YYYY-MM-DD)
        invoiceDateTime: invoiceDateTimeStr, // Full timestamp with time (local time, no timezone)
        partyId: formValue.partyId,
        billType: formValue.billType,
        items: formValue.items.map((item: any) => ({
          itemId: item.itemId,
          quantity: item.quantity,
          unitPrice: item.unitPrice
        })),
        discountPercent: this.totals.discountPercent,
        discountAmount: this.totals.discount,
        paymentStatus: formValue.paymentStatus,
        paidAmount: formValue.paidAmount || 0,
        receivedCashAmount: formValue.receivedCashAmount != null ? formValue.receivedCashAmount : 0,
        receivedOnlineAmount: formValue.receivedOnlineAmount != null ? formValue.receivedOnlineAmount : 0,
        balanceAmount: formValue.balanceAmount || this.calculateBalanceAmount(),
        modeOfPayment: formValue.modeOfPayment || 'CASH',
        onlinePaymentMethod: formValue.onlinePaymentMethod || null,
        onlinePaymentReference: formValue.onlinePaymentReference || null,
        notes: formValue.notes,
        subtotal: this.totals.subtotal,
        cgst: this.totals.cgst,
        sgst: this.totals.sgst,
        totalAmount: this.totals.total,
        status: status
      };
      
      const request = this.isEdit && this.billId
        ? this.apiService.put('/invoices', this.billId, bill)
        : this.apiService.post('/invoices', bill);
      
      request.subscribe({
        next: (response: any) => {
          this.loading = false;
          // Update invoiceNumber from response if auto-generated
          if (response?.invoiceNumber) {
            this.invoiceNumber = response.invoiceNumber;
            this.billingForm.patchValue({ invoiceNumber: response.invoiceNumber });
          }
          // Update billId if this was a create operation
          if (!this.isEdit && response?.id) {
            this.billId = response.id;
          }
          this.toastService.success('Success', `Bill ${this.isEdit ? 'updated' : 'created'} successfully`);
          // Refresh items to get updated stock quantities
          this.loadItems();
          setTimeout(() => {
            this.router.navigate(['/master/billing']);
          }, 500);
        },
        error: (err) => {
          this.loading = false;
          // Handle Bill No duplicate error
          if (err.status === 409) {
            this.billNumberError = err.error?.message || 'Bill No already exists';
            this.billingForm.get('invoiceNumber')?.setErrors({ duplicate: true });
            this.toastService.error('Error', err.error?.message || 'Bill No already exists');
          } else {
            this.toastService.error('Error', err.error?.message || `Failed to ${this.isEdit ? 'update' : 'create'} bill`);
          }
        }
      });
    }
  }

  cancel() {
    this.router.navigate(['/master/billing']);
  }

  editBill() {
    if (this.billId) {
      this.router.navigate(['/master/billing/edit', this.billId]);
    }
  }
  
  saveAndPrint() {
    if (this.billingForm.invalid) {
      this.toastService.error('Error', 'Please fill all required fields');
      return;
    }
    
    // Validate partial payment before submitting
    if (!this.validatePartialPayment()) {
      return;
    }
    
    // Save the bill first, then print
    this.loading = true;
    const formValue = this.billingForm.value;
    
    let status = 'NORMAL';
    if (this.isEdit) {
      if (this.originalBill) {
        const totalChanged = Math.abs(this.originalBill.totalAmount - this.totals.total) > 0.01;
        if (this.hasManualEdits || totalChanged) {
          status = 'CORRECTED';
        }
      }
    }
    
    // Format date-time manually to preserve local time (avoid UTC conversion)
    const dateObj = new Date(formValue.invoiceDate);
    const now = new Date();
    const hours = now.getHours();
    const minutes = now.getMinutes();
    
    // Format date as YYYY-MM-DD
    const year = dateObj.getFullYear();
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');
    const day = String(dateObj.getDate()).padStart(2, '0');
    const invoiceDateStr = `${year}-${month}-${day}`;
    
    // Format date-time as YYYY-MM-DDTHH:mm:ss (local time, no timezone)
    const invoiceDateTimeStr = `${invoiceDateStr}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
    
    const bill = {
      invoiceNumber: formValue.invoiceNumber?.trim() || null,
      invoiceDate: invoiceDateStr, // Date only (YYYY-MM-DD)
      invoiceDateTime: invoiceDateTimeStr, // Full timestamp with time (local time, no timezone)
      partyId: formValue.partyId,
      billType: formValue.billType,
      items: formValue.items.map((item: any) => ({
        itemId: item.itemId,
        quantity: item.quantity,
        unitPrice: item.unitPrice
      })),
      discountPercent: this.totals.discountPercent,
      discountAmount: this.totals.discount,
      paymentStatus: formValue.paymentStatus,
      paidAmount: formValue.paidAmount || 0,
      receivedCashAmount: formValue.receivedCashAmount != null ? formValue.receivedCashAmount : 0,
      receivedOnlineAmount: formValue.receivedOnlineAmount != null ? formValue.receivedOnlineAmount : 0,
      balanceAmount: formValue.balanceAmount || this.calculateBalanceAmount(),
      modeOfPayment: formValue.modeOfPayment || 'CASH',
      onlinePaymentMethod: formValue.onlinePaymentMethod || null,
      onlinePaymentReference: formValue.onlinePaymentReference || null,
      notes: formValue.notes,
      subtotal: this.totals.subtotal,
      cgst: this.totals.cgst,
      sgst: this.totals.sgst,
      totalAmount: this.totals.total,
      status: status
    };
    
    const request = this.isEdit && this.billId
      ? this.apiService.put('/invoices', this.billId, bill)
      : this.apiService.post('/invoices', bill);
    
    request.subscribe({
      next: (response: any) => {
        this.loading = false;
        if (response?.invoiceNumber) {
          this.invoiceNumber = response.invoiceNumber;
          this.billingForm.patchValue({ invoiceNumber: response.invoiceNumber });
        }
        if (!this.isEdit && response?.id) {
          this.billId = response.id;
        }
        this.toastService.success('Success', 'Bill saved successfully');
        this.loadItems();
        // After successful save, trigger print
        setTimeout(() => {
          this.printInvoice();
        }, 500);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          this.billNumberError = err.error?.message || 'Bill No already exists';
          this.billingForm.get('invoiceNumber')?.setErrors({ duplicate: true });
          this.toastService.error('Error', err.error?.message || 'Bill No already exists');
        } else {
          this.toastService.error('Error', err.error?.message || 'Failed to save bill');
        }
      }
    });
  }
  
  printInvoice() {
    if (!this.billId) {
      this.toastService.warning('Warning', 'Cannot print: Bill not saved yet');
      return;
    }
    
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    // Use /print endpoint and fetch with authentication headers
    this.http.get(`${apiUrl}/invoices/${this.billId}/print`, { 
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
      setTimeout(() => {
              printWindow.print();
              // Clean up blob URL after printing
          setTimeout(() => {
                window.URL.revokeObjectURL(url);
          }, 1000);
      }, 500);
    };
        } else {
          // Fallback: create link and trigger download, then user can print
          const link = document.createElement('a');
          link.href = url;
          link.target = '_blank';
          link.click();
          this.toastService.info('Info', 'PDF opened in new tab. Please use browser print option.');
          setTimeout(() => {
            window.URL.revokeObjectURL(url);
          }, 1000);
        }
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to generate PDF for printing');
        console.error('Print error:', err);
      }
    });
  }
  
  downloadInvoice() {
    if (!this.billId) {
      this.toastService.warning('Warning', 'Cannot download: Bill not saved yet');
      return;
    }
    
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    // Use /pdf endpoint for download (attachment)
    this.http.get(`${apiUrl}/invoices/${this.billId}/pdf`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        // Extract filename from Content-Disposition header or use default
        let filename = `invoice-${this.invoiceNumber || this.billId}.pdf`;
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

  canReturnBill(): boolean {
    // Can return only if:
    // 1. It's a GST invoice (not Estimate)
    // 2. Status is not already RETURNED
    // 3. Status is not DRAFT
    const billType = this.billingForm.get('billType')?.value;
    const status = this.billingForm.get('status')?.value;
    return billType === 'GST' && status !== 'RETURNED' && status !== 'DRAFT';
  }

  saveAsDraft() {
    if (this.billingForm.get('partyId')?.invalid) {
      this.toastService.error('Error', 'Please select a party');
      return;
    }
    
    if (this.itemsArray.length === 0) {
      this.toastService.error('Error', 'Please add at least one item');
      return;
    }
    
    this.loading = true;
    const formValue = this.billingForm.value;
    
    // Format date-time manually to preserve local time
    const dateObj = new Date(formValue.invoiceDate);
    const now = new Date();
    const hours = now.getHours();
    const minutes = now.getMinutes();
    
    const year = dateObj.getFullYear();
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');
    const day = String(dateObj.getDate()).padStart(2, '0');
    const invoiceDateStr = `${year}-${month}-${day}`;
    const invoiceDateTimeStr = `${invoiceDateStr}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
    
    const bill = {
      invoiceNumber: formValue.invoiceNumber?.trim() || null,
      invoiceDate: invoiceDateStr,
      invoiceDateTime: invoiceDateTimeStr,
      partyId: formValue.partyId,
      billType: formValue.billType,
      items: formValue.items.map((item: any) => ({
        itemId: item.itemId,
        quantity: item.quantity,
        unitPrice: item.unitPrice
      })),
      discountPercent: this.totals.discountPercent,
      discountAmount: this.totals.discount,
      paymentStatus: formValue.paymentStatus,
      paidAmount: formValue.paidAmount || 0,
      receivedCashAmount: formValue.receivedCashAmount != null ? formValue.receivedCashAmount : 0,
      receivedOnlineAmount: formValue.receivedOnlineAmount != null ? formValue.receivedOnlineAmount : 0,
      balanceAmount: formValue.balanceAmount || this.calculateBalanceAmount(),
      modeOfPayment: formValue.modeOfPayment || 'CASH',
      onlinePaymentMethod: formValue.onlinePaymentMethod || null,
      onlinePaymentReference: formValue.onlinePaymentReference || null,
      notes: formValue.notes,
      subtotal: this.totals.subtotal,
      cgst: this.totals.cgst,
      sgst: this.totals.sgst,
      totalAmount: this.totals.total,
      status: 'DRAFT' // Save as DRAFT
    };
    
    const request = this.isEdit && this.billId
      ? this.apiService.put('/invoices', this.billId, bill)
      : this.apiService.post('/invoices', bill);
    
    request.subscribe({
      next: (response: any) => {
        this.loading = false;
        if (response?.invoiceNumber) {
          this.invoiceNumber = response.invoiceNumber;
          this.billingForm.patchValue({ invoiceNumber: response.invoiceNumber });
        }
        if (!this.isEdit && response?.id) {
          this.billId = response.id;
        }
        this.toastService.success('Success', 'Bill saved as draft successfully');
        setTimeout(() => {
          this.router.navigate(['/master/billing']);
        }, 500);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          this.billNumberError = err.error?.message || 'Bill No already exists';
          this.billingForm.get('invoiceNumber')?.setErrors({ duplicate: true });
          this.toastService.error('Error', err.error?.message || 'Bill No already exists');
        } else {
          this.toastService.error('Error', err.error?.message || 'Failed to save draft');
        }
      }
    });
  }

  returnBill() {
    if (!this.billId) {
      this.toastService.warning('Warning', 'Cannot return: Bill not saved yet');
      return;
    }

    // Validate only critical fields needed for returning (not all form validations)
    const partyId = this.billingForm.get('partyId')?.value;
    const invoiceDate = this.billingForm.get('invoiceDate')?.value;
    const billType = this.billingForm.get('billType')?.value;
    const items = this.itemsArray.length;
    
    if (!partyId) {
      this.toastService.warning('Warning', 'Please select a party before returning the invoice');
      return;
    }
    
    if (!invoiceDate) {
      this.toastService.warning('Warning', 'Please select a date before returning the invoice');
      return;
    }
    
    if (items === 0) {
      this.toastService.warning('Warning', 'Please add at least one item before returning the invoice');
      return;
    }
    
    // Validate that all items have required fields
    for (let i = 0; i < this.itemsArray.length; i++) {
      const itemGroup = this.itemsArray.at(i) as FormGroup;
      const itemId = itemGroup.get('itemId')?.value;
      const quantity = itemGroup.get('quantity')?.value;
      const unitPrice = itemGroup.get('unitPrice')?.value;
      
      if (!itemId) {
        this.toastService.warning('Warning', `Please select an item for row ${i + 1} before returning the invoice`);
        return;
      }
      
      if (!quantity || quantity <= 0) {
        this.toastService.warning('Warning', `Please enter a valid quantity for row ${i + 1} before returning the invoice`);
        return;
      }
      
      if (!unitPrice || unitPrice < 0) {
        this.toastService.warning('Warning', `Please enter a valid unit price for row ${i + 1} before returning the invoice`);
        return;
      }
    }
    
    // For GST bills, payment status should be valid if present
    if (billType === 'GST') {
      const paymentStatus = this.billingForm.get('paymentStatus')?.value;
      if (!paymentStatus) {
        this.toastService.warning('Warning', 'Please select payment status before returning the invoice');
        return;
      }
    }

    // Confirm return action
    if (!confirm('Are you sure you want to return this invoice? This will save all changes, restore stock and mark the invoice as returned. This action cannot be undone.')) {
      return;
    }

    this.loading = true;
    
    // Prepare the full invoice data with all form changes
    const formValue = this.billingForm.value;
    
    // Format date and date-time
    const dateObj = new Date(formValue.invoiceDate);
    const now = new Date();
    const hours = now.getHours();
    const minutes = now.getMinutes();
    
    const year = dateObj.getFullYear();
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');
    const day = String(dateObj.getDate()).padStart(2, '0');
    const invoiceDateStr = `${year}-${month}-${day}`;
    const invoiceDateTimeStr = `${invoiceDateStr}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
    
    // Build the complete invoice object with all form data
    const invoiceData = {
      invoiceNumber: formValue.invoiceNumber?.trim() || null,
      invoiceDate: invoiceDateStr,
      invoiceDateTime: invoiceDateTimeStr,
      partyId: formValue.partyId,
      billType: formValue.billType,
      items: formValue.items.map((item: any) => {
        // Find item in availableItems to get itemCode and itemName
        const selectedItem = this.availableItems.find(it => it.id === item.itemId);
        return {
          itemId: item.itemId,
          itemCode: selectedItem?.itemCode || null,
          itemName: selectedItem?.itemName || item.itemSearch || null,
          quantity: item.quantity,
          unitPrice: item.unitPrice
        };
      }),
      discountPercent: this.totals.discountPercent,
      discountAmount: this.totals.discount,
      paymentStatus: formValue.paymentStatus,
      paidAmount: formValue.paidAmount || 0,
      receivedCashAmount: formValue.receivedCashAmount != null ? formValue.receivedCashAmount : 0,
      receivedOnlineAmount: formValue.receivedOnlineAmount != null ? formValue.receivedOnlineAmount : 0,
      balanceAmount: formValue.balanceAmount || this.calculateBalanceAmount(),
      modeOfPayment: formValue.modeOfPayment || 'CASH',
      onlinePaymentMethod: formValue.onlinePaymentMethod || null,
      onlinePaymentReference: formValue.onlinePaymentReference || null,
      notes: formValue.notes, // Include Notes and all other fields
      subtotal: this.totals.subtotal,
      cgst: this.totals.cgst,
      sgst: this.totals.sgst,
      totalAmount: this.totals.total,
      status: 'RETURNED' // Set status to RETURNED
    };
    
    // Send full invoice data to return endpoint
    this.apiService.post<any>(`/invoices/${this.billId}/return`, invoiceData).subscribe({
      next: (returnedInvoice) => {
        this.loading = false;
        this.toastService.success('Success', 'Invoice returned successfully. All changes saved and stock has been restored.');
        
        // Reload the invoice to get updated data
        this.loadBill(this.billId!);
        
        // Disable form since returned invoices cannot be edited
        this.billingForm.disable();
        this.isView = true;
      },
      error: (err) => {
        this.loading = false;
        const message = err.error?.message || 'Failed to return invoice';
        this.toastService.error('Error', message);
      }
    });
  }
}

