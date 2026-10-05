import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Location } from '@angular/common';
import { ApiService } from '../../../../core/services/api.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import { PincodeLookupService } from '../../../../core/services/pincode-lookup.service';
import { SmallClientEntryQuotationService } from '../../../../core/services/small-client-entry.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { SmallClientEntryQuotation, SmallClientEntry } from '../../../../core/models/small-client-entry.model';
import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatDialog } from '@angular/material/dialog';
import { PdfConfirmDialogComponent } from '../small-client-entry-list/small-client-entry-list.component';
import { ExcelExportService } from '../../../../core/services/excel-export.service';
import {
  filenameFromContentDisposition,
  monthlyBreakupPdfFallback,
  monthlyInvoiceDownloadFilename
} from '../../../../core/utils/content-disposition-filename.util';
import { formatLocalDateOnly, parseIsoDateToLocal } from '../../../../core/utils/date-only.util';

@Component({
  selector: 'app-small-client-entry-form',
  template: `
    <div class="cq-page">
      <div class="cq-header">
        <div>
          <h1 class="cq-title">{{ quotationId ? 'Small Client Entry' : 'Create Small Client Entry' }}</h1>
          <span *ngIf="quotation?.title" class="cq-qno">{{ quotation?.title }}</span>
        </div>
        <div class="cq-hdr-actions">
          <button mat-stroked-button color="primary" (click)="goBack()" matTooltip="Go Back">
            <mat-icon>arrow_back</mat-icon> Back
          </button>
        </div>
      </div>

      <!-- HEADER FORM CARD -->
      <mat-card class="cq-meta-card">
        <form [formGroup]="headerForm">
          <div class="cq-meta-grid">
            <div class="cq-customer-row cq-col-span-1-to-2">
              <mat-form-field appearance="outline" class="cq-customer-field">
                <mat-label>Customer</mat-label>
                <input matInput formControlName="customerSearch"
                       [matAutocomplete]="customerAuto"
                       (input)="onCustomerInput($event)"
                       (keyup)="onCustomerKeyUp($event)"
                       placeholder="Type to search customer..."
                       [readonly]="quotationId != null">
                <mat-autocomplete #customerAuto="matAutocomplete" [displayWith]="customerDisplay"
                                  (optionSelected)="onCustomerSelected($event)">
                  <mat-option *ngFor="let c of filteredCustomers" [value]="c">
                    <span style="font-weight:600">{{ c.partyName }}</span>
                    <span *ngIf="c.partyCode" style="color:#6B7280;font-size:11px;margin-left:8px">{{ c.partyCode }}</span>
                  </mat-option>
                  <mat-option *ngIf="filteredCustomers.length === 0 && customerSearchTerm.length >= 2" disabled>
                    No customers found
                  </mat-option>
                </mat-autocomplete>
                <mat-error>Customer is required</mat-error>
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline">
              <mat-label>Month *</mat-label>
              <mat-select formControlName="month" [disabled]="quotationId != null">
                <mat-option *ngFor="let m of months" [value]="m">{{m}}</mat-option>
              </mat-select>
              <mat-error>Month is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Year *</mat-label>
              <input matInput type="number" class="cq-no-spinner" formControlName="year" [readonly]="quotationId != null">
              <mat-error>Year is required</mat-error>
            </mat-form-field>

            <div class="cq-col-span-1 cq-action-btn-container" *ngIf="!quotationId">
              <button mat-raised-button color="primary" (click)="createQuotation()" [disabled]="loading || headerForm.invalid">
                <mat-icon>save</mat-icon> Create Monthly Entry
              </button>
            </div>
            
             <mat-form-field appearance="outline" class="cq-col-span-1-to-2" *ngIf="quotationId">
              <mat-label>Title</mat-label>
              <input matInput formControlName="title">
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="quotationId">
              <mat-label>Invoice No</mat-label>
              <input matInput formControlName="invoiceNumber" placeholder="Leave blank to auto-generate">
              <mat-hint>Enter custom Invoice No or leave blank to auto-generate</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="quotationId">
              <mat-label>Invoice Date</mat-label>
              <input matInput [matDatepicker]="invoiceDatePicker" formControlName="invoiceDate">
              <mat-datepicker-toggle matSuffix [for]="invoiceDatePicker"></mat-datepicker-toggle>
              <mat-datepicker #invoiceDatePicker></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline" class="cq-col-span-1-to-2" *ngIf="quotationId">
              <mat-label>Note</mat-label>
              <input matInput formControlName="note" placeholder="Internal remarks (not shown on invoice or breakup)">
              <mat-hint>Internal use only; not included in PDF/Excel</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="quotationId">
              <mat-label>Fuel Charges %</mat-label>
              <input matInput type="number" class="cq-no-spinner" step="0.01" formControlName="fuelChargePercentage" placeholder="From quotation if blank">
              <mat-hint>Override courier quotation fuel %</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="quotationId">
              <mat-label>FOV Charges %</mat-label>
              <input matInput type="number" class="cq-no-spinner" step="0.01" formControlName="fovCharges" placeholder="From quotation if blank">
              <mat-hint>Override courier quotation FOV %</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="quotationId">
              <mat-label>GST Percentage (%)</mat-label>
              <input matInput type="number" class="cq-no-spinner" step="0.01" min="0" max="100" formControlName="gstPercentage" placeholder="Company default if blank">
              <mat-hint>0–100; blank uses company default then 0%</mat-hint>
            </mat-form-field>
            <div class="cq-col-span-1 cq-action-btn-container" *ngIf="quotationId">
              <button mat-stroked-button color="primary" (click)="updateQuotation()" [disabled]="loading">
                <mat-icon>update</mat-icon> Update Settings
              </button>
            </div>
            <app-last-updated-by-field class="cq-col-span-1-to-2" *ngIf="quotationId" [value]="lastUpdatedBy"></app-last-updated-by-field>
          </div>
        </form>
      </mat-card>

      <!-- ADD ENTRY CARD -->
      <mat-card class="cq-meta-card" *ngIf="quotationId" style="background-color: #f8fafc; border: 1px dashed #cbd5e1; box-shadow: none;">
         <div class="cq-doc-title" style="border:none; margin-bottom: 12px; color: #334155;">ADD NEW SHIPMENT</div>
         <div class="cq-meta-grid">
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Date *</mat-label>
                  <input matInput [matDatepicker]="newEntryDatePicker" [(ngModel)]="newEntry.entryDate" (dateChange)="clearNewEntryFieldError('entryDate')" (input)="clearNewEntryFieldError('entryDate')">
                  <mat-datepicker-toggle matSuffix [for]="newEntryDatePicker"></mat-datepicker-toggle>
                  <mat-datepicker #newEntryDatePicker></mat-datepicker>
                  <mat-error *ngIf="newEntryFieldErrors['entryDate']">{{ newEntryFieldErrors['entryDate'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Rate Type *</mat-label>
                  <mat-select [(ngModel)]="newEntry.rateType" (selectionChange)="onNewRateTypeChange(); clearNewEntryFieldError('rateType')">
                    <mat-option value="STANDARD">Standard</mat-option>
                    <mat-option value="EXPRESS_RATE">Express Rate</mat-option>
                    <mat-option value="SURFACE_RATE">Surface Rate</mat-option>
                    <mat-option value="SafetyPlus">Surface</mat-option>
                    <mat-option value="PriorityClass">Safety(Priority)</mat-option>
                  </mat-select>
                  <mat-error *ngIf="newEntryFieldErrors['rateType']">{{ newEntryFieldErrors['rateType'] }}</mat-error>
                  <mat-hint>Rate set for amount calculation</mat-hint>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Zone *</mat-label>
                  <mat-select [(ngModel)]="newEntry.zone" (selectionChange)="onNewWeightChanged(); clearNewEntryFieldError('zone')">
                    <mat-option *ngFor="let z of zones" [value]="z.value">{{ z.label }}</mat-option>
                  </mat-select>
                  <mat-error *ngIf="newEntryFieldErrors['zone']">{{ newEntryFieldErrors['zone'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1-to-2">
               <mat-form-field appearance="outline">
                  <mat-label>Consignor *</mat-label>
                  <input matInput [(ngModel)]="newEntry.consignor" (input)="clearNewEntryFieldError('consignor')">
                  <mat-error *ngIf="newEntryFieldErrors['consignor']">{{ newEntryFieldErrors['consignor'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1-to-2">
               <mat-form-field appearance="outline">
                  <mat-label>Receiver Name *</mat-label>
                  <input matInput [(ngModel)]="newEntry.receiverName" (input)="clearNewEntryFieldError('receiverName')">
                  <mat-error *ngIf="newEntryFieldErrors['receiverName']">{{ newEntryFieldErrors['receiverName'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Receiver Phone No</mat-label>
                  <input matInput [(ngModel)]="newEntry.receiverPhoneNo" placeholder="Phone No">
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Pincode *</mat-label>
                  <input matInput [(ngModel)]="newEntry.pincode" (input)="onPincodeChange('new'); clearNewEntryFieldError('pincode')" maxlength="16">
                  <span matSuffix *ngIf="pincodeLoading['new']" style="font-size: 12px; margin-right: 8px;">Loading...</span>
                  <mat-error *ngIf="newEntryFieldErrors['pincode']">{{ newEntryFieldErrors['pincode'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>State</mat-label>
                  <input matInput [(ngModel)]="newEntry.state" [disabled]="stateDisabled['new']">
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Area Name</mat-label>
                  <mat-select *ngIf="pincodeAreas['new'] && pincodeAreas['new'].length > 1; else singleAreaNew" [(ngModel)]="newEntry.areaName"
                    placeholder="Area name">
                    <mat-option *ngFor="let area of pincodeAreas['new']" [value]="area">{{area}}</mat-option>
                  </mat-select>
                  <ng-template #singleAreaNew>
                    <input matInput [(ngModel)]="newEntry.areaName" placeholder="Area name" [disabled]="areaDisabled['new']">
                  </ng-template>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1-to-2">
               <mat-form-field appearance="outline">
                  <mat-label>Full Address</mat-label>
                  <textarea matInput [(ngModel)]="newEntry.fullAddress" rows="1"></textarea>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Courier *</mat-label>
                  <input matInput [(ngModel)]="newEntry.courierType" [matAutocomplete]="courierAuto" (blur)="onNewWeightChanged()" (input)="clearNewEntryFieldError('courierType'); onCiFilterInput($event, 'courier', 'new')">
                  <mat-autocomplete #courierAuto="matAutocomplete">
                    <mat-option *ngFor="let option of filteredCouriersNew" [value]="option">{{ option }}</mat-option>
                  </mat-autocomplete>
                  <mat-error *ngIf="newEntryFieldErrors['courierType']">{{ newEntryFieldErrors['courierType'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Weight (Kg) *</mat-label>
                  <input matInput type="number" class="cq-no-spinner" step="0.001" [(ngModel)]="newEntry.weight" (blur)="onNewWeightChanged()" (input)="clearNewEntryFieldError('weight')">
                  <mat-error *ngIf="newEntryFieldErrors['weight']">{{ newEntryFieldErrors['weight'] }}</mat-error>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Tracking No</mat-label>
                  <input matInput [(ngModel)]="newEntry.trackingNumber">
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Item</mat-label>
                  <input matInput [(ngModel)]="newEntry.itemType" [matAutocomplete]="itemAuto" (input)="onCiFilterInput($event, 'item', 'new')">
                  <mat-autocomplete #itemAuto="matAutocomplete">
                    <mat-option *ngFor="let option of filteredItemsNew" [value]="option">{{ option }}</mat-option>
                  </mat-autocomplete>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Status</mat-label>
                  <input matInput [(ngModel)]="newEntry.deliveryStatus" [matAutocomplete]="statusAuto" (input)="onCiFilterInput($event, 'status', 'new')">
                  <mat-autocomplete #statusAuto="matAutocomplete">
                    <mat-option *ngFor="let option of filteredStatusesNew" [value]="option">{{ option }}</mat-option>
                  </mat-autocomplete>
               </mat-form-field>
            </div>

            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Amount (base)</mat-label>
                  <input matInput type="number" class="cq-no-spinner" step="0.01" [(ngModel)]="newEntry.amount" (input)="onNewAmountEdit()">
               </mat-form-field>
            </div>
            <div class="cq-col-span-1">
               <mat-form-field appearance="outline">
                  <mat-label>Additional Charges (optional)</mat-label>
                  <input matInput type="number" class="cq-no-spinner" step="0.01" [(ngModel)]="newEntry.additionalCharges" placeholder="0">
                  <mat-hint>Added to this shipment total</mat-hint>
               </mat-form-field>
            </div>
            <div class="cq-col-span-1 cq-total-addl" *ngIf="(newEntry.amount != null && newEntry.amount !== '') || (newEntry.additionalCharges != null && newEntry.additionalCharges !== '' && newEntry.additionalCharges > 0)">
               <strong>Total (Amount + Addl):</strong> ₹ {{ getNewEntryTotal() | number:'1.2-2' }}
            </div>
            <div class="cq-col-span-1-to-2">
               <mat-form-field appearance="outline">
                  <mat-label>Additional Charges Description (optional)</mat-label>
                  <input matInput [(ngModel)]="newEntry.additionalChargesDescription" placeholder="e.g. Handling Charges">
               </mat-form-field>
            </div>
            <div class="cq-col-span-1 cq-action-btn-container">
               <button mat-raised-button color="primary" (click)="addEntry()">
                 <mat-icon>add_circle</mat-icon> Add Entry
               </button>
            </div>
         </div>
      </mat-card>

      <!-- ENTRIES SETTINGS CARD -->
      <mat-card class="cq-rates-card" *ngIf="quotationId && entries.length > 0">
        <div class="cq-breakup-toolbar">
          <div class="cq-breakup-title cq-doc-title">MONTHLY SHIPMENT BREAKUP</div>
          <div class="cq-breakup-search">
            <mat-form-field appearance="outline" class="cq-search-field cq-search-field-narrow">
              <mat-label>Search By</mat-label>
              <mat-select [(ngModel)]="selectedSearchFields" multiple>
                <mat-option *ngFor="let option of searchFields" [value]="option.value">{{option.label}}</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="cq-search-field cq-search-field-wide">
              <mat-icon matPrefix>search</mat-icon>
              <input matInput [(ngModel)]="searchQuery" placeholder="Search…" (ngModelChange)="onBreakupSearchChange($event)" (keyup.enter)="$event.stopPropagation()">
              <button *ngIf="searchQuery || shipmentFocusEntryId" matSuffix mat-icon-button type="button" aria-label="Clear" (click)="clearShipmentBreakupFilter()">
                <mat-icon>close</mat-icon>
              </button>
            </mat-form-field>
          </div>
          <div class="cq-breakup-exports">
            <button mat-stroked-button type="button" (click)="exportFilteredBreakupExcel()" matTooltip="Export filtered rows only">
              <mat-icon>grid_on</mat-icon> Export Excel
            </button>
            <button mat-stroked-button type="button" color="primary" (click)="exportFilteredBreakupPdf()" matTooltip="Export filtered rows only">
              <mat-icon>picture_as_pdf</mat-icon> Export PDF
            </button>
          </div>
        </div>
        
        <div class="totals-section" *ngIf="quotation">
           <b>Summary: </b> 
           <span>Shipments: {{quotation.totalShipments}} | </span>
           <span>Weight: {{quotation.totalWeight | number:'1.3-3'}} Kg | </span>
           <span>Amount: ₹ {{quotation.totalAmount | number:'1.2-2'}}</span>
        </div>

        <div class="cq-table-wrap">
          <table class="cq-table">
            <thead>
              <tr>
                <th class="cq-th cq-th-sno">S.No</th>
                <th class="cq-th cq-th-date">Date</th>
                <th class="cq-th cq-th-courier">Courier</th>
                <th class="cq-th cq-th-tracking">AWB NO</th>
                <th class="cq-th cq-th-dest">Destination</th>
                <th class="cq-th cq-th-weight">Weight</th>
                <th class="cq-th cq-th-item">Item</th>
                <th class="cq-th cq-th-zone">Zone</th>
                <th class="cq-th cq-th-rate-type">RateType</th>
                <th class="cq-th cq-th-desc">Description</th>
                <th class="cq-th cq-th-consignor">Consignor</th>
                <th class="cq-th cq-th-status">Status</th>
                <th class="cq-th cq-th-addl">Addl Chgs</th>
                <th class="cq-th cq-th-amount">Courier Cost</th>
                <th class="cq-th" style="min-width:120px">Updated By</th>
                <th class="cq-th cq-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let entry of filteredEntries; let i = index">
                <td class="cq-td cq-td-sno" [class.cq-sno-editing]="editingIndex === i"
                    (click)="onSnoCellClick(i, entry)"
                    [matTooltip]="editingIndex === i ? '' : 'Click to edit; use tick / cross to save or cancel'">
                   <span *ngIf="editingIndex !== i">{{ i + 1 }}</span>
                   <div *ngIf="editingIndex === i" class="cq-sno-actions" (click)="$event.stopPropagation()">
                      <button mat-icon-button color="primary" type="button" class="cq-sno-action-btn" (click)="saveEdit()" matTooltip="Save"><mat-icon>check</mat-icon></button>
                      <button mat-icon-button color="warn" type="button" class="cq-sno-action-btn" (click)="cancelEdit()" matTooltip="Cancel"><mat-icon>close</mat-icon></button>
                   </div>
                </td>
                <td class="cq-td">
                  <span *ngIf="editingIndex !== i">{{entry.entryDate | date:'yyyy-MM-dd'}}</span>
                  <mat-form-field appearance="outline" *ngIf="editingIndex === i" style="width: 140px; margin-bottom: -1.25em;">
                    <input matInput [matDatepicker]="editPicker" [(ngModel)]="editingEntry.entryDate">
                    <mat-datepicker-toggle matSuffix [for]="editPicker"></mat-datepicker-toggle>
                    <mat-datepicker #editPicker></mat-datepicker>
                  </mat-form-field>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{entry.courierType}}</span>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-medium" [(ngModel)]="editingEntry.courierType" [matAutocomplete]="courierAutoEdit" (blur)="onEditWeightChanged()" (input)="onCiFilterInput($event, 'courier', 'edit')">
                   <mat-autocomplete #courierAutoEdit="matAutocomplete">
                     <mat-option *ngFor="let option of filteredCouriersEdit" [value]="option">{{ option }}</mat-option>
                   </mat-autocomplete>
                </td>
                <td class="cq-td" style="white-space: nowrap;">
                   <ng-container *ngIf="editingIndex !== i">
                     <a *ngIf="entry.trackingNumber" href="https://franchexpress.com/courier-tracking/" target="_blank" class="cq-tracking-link">
                        {{entry.trackingNumber}}
                     </a>
                     <button *ngIf="entry.trackingNumber" mat-icon-button class="cq-copy-btn" (click)="copyAwb(entry.trackingNumber)" matTooltip="Copy AWB">
                       <mat-icon style="font-size: 16px; width: 16px; height: 16px;">content_copy</mat-icon>
                     </button>
                     <span *ngIf="!entry.trackingNumber">-</span>
                   </ng-container>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-tracking" [(ngModel)]="editingEntry.trackingNumber">
                </td>
                <td class="cq-td cq-td-dest">
                   <span *ngIf="editingIndex !== i" class="cq-cell-text">{{entry.consigneeAddress}}</span>
                   <div *ngIf="editingIndex === i" style="display:flex; flex-direction:column; gap:4px">
                     <input class="cq-input" [(ngModel)]="editingEntry.receiverName" placeholder="Receiver Name">
                     <input class="cq-input" [(ngModel)]="editingEntry.receiverPhoneNo" placeholder="Receiver Phone No">
                     <div style="display:flex; gap:4px">
                       <input class="cq-input" [(ngModel)]="editingEntry.pincode" placeholder="Pincode" (input)="onPincodeChange('edit')" maxlength="16" style="width: 80px">
                       <input class="cq-input" [(ngModel)]="editingEntry.state" placeholder="State" [disabled]="stateDisabled['edit']" style="flex:1">
                     </div>
                     <select *ngIf="pincodeAreas['edit'] && pincodeAreas['edit'].length > 1; else singleAreaEdit" class="cq-input" [(ngModel)]="editingEntry.areaName">
                        <option *ngFor="let area of pincodeAreas['edit']" [value]="area">{{area}}</option>
                     </select>
                     <ng-template #singleAreaEdit>
                       <input class="cq-input" [(ngModel)]="editingEntry.areaName" placeholder="Area" [disabled]="areaDisabled['edit']">
                     </ng-template>
                     <input class="cq-input" [(ngModel)]="editingEntry.fullAddress" placeholder="Full Address">
                   </div>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{entry.weight | number:'1.3-3'}}</span>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-num cq-no-spinner" type="number" step="0.001" [(ngModel)]="editingEntry.weight" (blur)="onEditWeightChanged()">
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{entry.itemType}}</span>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-medium" [(ngModel)]="editingEntry.itemType" [matAutocomplete]="itemAutoEdit" (input)="onCiFilterInput($event, 'item', 'edit')">
                   <mat-autocomplete #itemAutoEdit="matAutocomplete">
                     <mat-option *ngFor="let option of filteredItemsEdit" [value]="option">{{ option }}</mat-option>
                   </mat-autocomplete>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{getZoneLabel(entry.zone)}}</span>
                   <mat-select *ngIf="editingIndex === i" class="cq-input cq-input-select" [(ngModel)]="editingEntry.zone" (selectionChange)="onEditWeightChanged()">
                     <mat-option *ngFor="let z of zones" [value]="z.value">{{ z.label }}</mat-option>
                   </mat-select>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{getRateTypeLabel(entry.rateType)}}</span>
                   <mat-select *ngIf="editingIndex === i" class="cq-input cq-input-select" [(ngModel)]="editingEntry.rateType" (selectionChange)="onEditRateTypeChange()">
                    <mat-option value="STANDARD">Standard</mat-option>
                    <mat-option value="EXPRESS_RATE">Express Rate</mat-option>
                    <mat-option value="SURFACE_RATE">Surface Rate</mat-option>
                    <mat-option value="SafetyPlus">Surface</mat-option>
                    <mat-option value="PriorityClass">Safety(Priority)</mat-option>
                   </mat-select>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i" class="cq-truncate" [title]="entry.additionalChargesDescription">{{entry.additionalChargesDescription}}</span>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-medium" [(ngModel)]="editingEntry.additionalChargesDescription" placeholder="">
                </td>
                <td class="cq-td">
                   <div *ngIf="editingIndex !== i" class="cq-truncate" [title]="entry.consignor">{{entry.consignor}}</div>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-medium" [(ngModel)]="editingEntry.consignor">
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{entry.deliveryStatus}}</span>
                   <div *ngIf="editingIndex === i" style="display:flex;flex-direction:column;gap:4px">
                     <input class="cq-input cq-input-medium" [(ngModel)]="editingEntry.deliveryStatus" [matAutocomplete]="statusAutoEdit" (input)="onCiFilterInput($event, 'status', 'edit')">
                     <mat-autocomplete #statusAutoEdit="matAutocomplete">
                       <mat-option *ngFor="let option of filteredStatusesEdit" [value]="option">{{ option }}</mat-option>
                     </mat-autocomplete>
                   </div>
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{entry.additionalCharges != null && entry.additionalCharges > 0 ? (entry.additionalCharges | number:'1.2-2') : ''}}</span>
                   <input *ngIf="editingIndex === i" class="cq-input cq-input-num cq-no-spinner" type="number" step="0.01" [(ngModel)]="editingEntry.additionalCharges" placeholder="0">
                </td>
                <td class="cq-td">
                   <span *ngIf="editingIndex !== i">{{ (entry.amount != null ? entry.amount : 0) + (entry.additionalCharges != null ? entry.additionalCharges : 0) | number:'1.2-2' }}</span>
                   <ng-container *ngIf="editingIndex === i">
                     <input class="cq-input cq-input-num cq-no-spinner" type="number" step="0.01" [(ngModel)]="editingEntry.amount" (input)="onAmountEdit()">
                     <div class="cq-cell-total">Row total: ₹ {{ getEntryTotal(editingEntry) | number:'1.2-2' }}</div>
                   </ng-container>
                </td>
                <td class="cq-td" style="font-size:12px; color:#6B7280; white-space:nowrap;">{{ entry.lastUpdatedBy || '—' }}</td>
                <td class="cq-td">
                  <div *ngIf="editingIndex !== i" style="display: flex; justify-content: center;">
                    <button mat-icon-button color="primary" (click)="startEdit(i, entry)"><mat-icon>edit</mat-icon></button>
                    <button mat-icon-button color="warn" (click)="deleteEntry(entry.id!)"><mat-icon>delete</mat-icon></button>
                  </div>
                  <div *ngIf="editingIndex === i" style="display: flex; justify-content: center;">
                     <button mat-icon-button color="primary" (click)="saveEdit()"><mat-icon>check</mat-icon></button>
                     <button mat-icon-button (click)="cancelEdit()"><mat-icon>close</mat-icon></button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </mat-card>

      <div class="cq-action-bar" *ngIf="quotationId" style="align-items: center;">
        <div style="display: flex; align-items: center; gap: 16px; margin-right: 16px; font-size: 14px; font-weight: 500;">
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer;">
            <input type="checkbox" id="chkGst" [(ngModel)]="includeGst" style="width: 16px; height: 16px; cursor: pointer;">
            Include GST?
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer;">
            <input type="checkbox" id="chkFuel" [(ngModel)]="includeFuel" style="width: 16px; height: 16px; cursor: pointer;">
            Include Fuel?
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer;">
            <input type="checkbox" id="chkWeight" [(ngModel)]="includeWeight" style="width: 16px; height: 16px; cursor: pointer;">
            Include Weight?
          </label>
        </div>
        <button mat-raised-button color="accent" type="button" (click)="generateInvoice()" [disabled]="loading">
          <mat-icon>receipt</mat-icon>
          Generate Invoice
        </button>
        <button mat-stroked-button (click)="downloadPdf()">
          <mat-icon>picture_as_pdf</mat-icon> Download PDF
        </button>
        <button mat-stroked-button type="button" (click)="viewInvoice()">
          <mat-icon>visibility</mat-icon> View Invoice
        </button>
        <button mat-stroked-button type="button" (click)="printInvoice()">
          <mat-icon>print</mat-icon> Print Invoice
        </button>
        <button mat-stroked-button (click)="downloadWord()">
          <mat-icon>description</mat-icon> Download Word
        </button>
        <button mat-stroked-button color="primary" (click)="toggleEmailOptions()">
          <mat-icon>mail</mat-icon> Send Email
        </button>
      </div>

      <mat-card class="cq-meta-card cq-email-card" *ngIf="quotationId && showEmailOptions">
        <div class="cq-doc-title" style="border:none; margin-bottom: 12px;">SEND EMAIL</div>
        <div class="cq-meta-grid">
          <mat-form-field appearance="outline">
            <mat-label>Email Type</mat-label>
            <mat-select [(ngModel)]="emailOptions.emailType" (selectionChange)="onEmailTypeChange()">
              <mat-option value="MONTHLY">Monthly</mat-option>
              <mat-option value="DAILY">Daily</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To Addresses *</mat-label>
            <mat-select [(ngModel)]="emailOptions.toAddresses" multiple required>
              <mat-option *ngFor="let em of availableEmails" [value]="em">{{em}}</mat-option>
            </mat-select>
            <mat-hint>Only addresses saved on the client are listed</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Attachment Format</mat-label>
            <mat-select [(ngModel)]="emailOptions.attachmentFormat">
              <mat-option value="PDF">PDF</mat-option>
              <mat-option value="EXCEL">Excel</mat-option>
            </mat-select>
          </mat-form-field>
          <ng-container *ngIf="emailOptions.emailType === 'DAILY'">
            <mat-form-field appearance="outline">
              <mat-label>From Date *</mat-label>
              <input matInput [matDatepicker]="emailFromDatePicker" [(ngModel)]="emailOptions.dailyFromDate" (dateChange)="onDailyDateRangeChanged()" required>
              <mat-datepicker-toggle matSuffix [for]="emailFromDatePicker"></mat-datepicker-toggle>
              <mat-datepicker #emailFromDatePicker></mat-datepicker>
              <mat-hint>Start date for shipment breakup</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>To Date *</mat-label>
              <input matInput [matDatepicker]="emailToDatePicker" [(ngModel)]="emailOptions.dailyToDate" (dateChange)="onDailyDateRangeChanged()" required>
              <mat-datepicker-toggle matSuffix [for]="emailToDatePicker"></mat-datepicker-toggle>
              <mat-datepicker #emailToDatePicker></mat-datepicker>
              <mat-hint>End date (same as From = single day)</mat-hint>
            </mat-form-field>
            <div class="cq-daily-selection cq-col-span-1-to-2" *ngIf="dailyEmailEntries.length || (emailOptions.dailyFromDate && emailOptions.dailyToDate)">
              <div class="cq-daily-selection__head">
                <div class="cq-daily-selection__title">Select records to send</div>
                <div class="cq-daily-selection__count" *ngIf="dailyEmailEntries.length">
                  {{ dailySelectedCount }} of {{ dailyEmailEntries.length }} records selected
                </div>
              </div>
              <div class="cq-daily-selection__empty" *ngIf="!dailyEmailEntries.length">
                No records found for selected date range.
              </div>
              <div class="cq-daily-selection__table-wrap" *ngIf="dailyEmailEntries.length">
                <table class="cq-daily-selection__table">
                  <thead>
                    <tr>
                      <th>
                        <input
                          type="checkbox"
                          [checked]="isAllDailySelected()"
                          [indeterminate]="isDailySelectionIndeterminate()"
                          (change)="toggleSelectAllDaily($any($event.target).checked)"
                          aria-label="Select all rows" />
                      </th>
                      <th>Date</th>
                      <th>Client Name</th>
                      <th>AWB No</th>
                      <th>Amount</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let entry of dailyEmailEntries">
                      <td>
                        <input
                          type="checkbox"
                          [checked]="isDailyEntrySelected(entry.id)"
                          (change)="toggleDailyEntrySelection(entry.id, $any($event.target).checked)"
                          aria-label="Select row" />
                      </td>
                      <td>{{ entry.entryDate | date:'yyyy-MM-dd' }}</td>
                      <td>{{ quotation?.customerName || headerForm.get('customerName')?.value || '—' }}</td>
                      <td>{{ entry.trackingNumber || '—' }}</td>
                      <td>{{ getEntryTotal(entry) | number:'1.2-2' }}</td>
                      <td>{{ entry.deliveryStatus || '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </ng-container>
          <ng-container *ngIf="emailOptions.attachmentFormat">
            <mat-form-field appearance="outline">
              <mat-label>Include Amount in {{ emailOptions.attachmentFormat === 'PDF' ? 'PDF' : 'Excel' }}</mat-label>
              <mat-select [(ngModel)]="emailOptions.includeAmountInShipmentBreakup">
                <mat-option [value]="true">Yes</mat-option>
                <mat-option [value]="false">No</mat-option>
              </mat-select>
              <mat-hint>Show amount column in attachment</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="emailOptions.attachmentFormat === 'PDF' || emailOptions.emailType === 'MONTHLY'">
              <mat-label>Include GST?</mat-label>
              <mat-select [(ngModel)]="emailOptions.includeGst">
                <mat-option [value]="true">Yes</mat-option>
                <mat-option [value]="false">No</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="emailOptions.attachmentFormat === 'PDF' || emailOptions.emailType === 'MONTHLY'">
              <mat-label>Include Fuel?</mat-label>
              <mat-select [(ngModel)]="emailOptions.includeFuel">
                <mat-option [value]="true">Yes</mat-option>
                <mat-option [value]="false">No</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="emailOptions.attachmentFormat">
              <mat-label>Include Weight in attachment?</mat-label>
              <mat-select [(ngModel)]="emailOptions.includeWeight">
                <mat-option [value]="true">Yes</mat-option>
                <mat-option [value]="false">No</mat-option>
              </mat-select>
              <mat-hint>PDF / Excel shipment columns</mat-hint>
            </mat-form-field>
          </ng-container>
        </div>
        <div class="cq-action-bar">
          <button mat-raised-button color="primary" (click)="sendEmail()" [disabled]="loading || !emailOptions.toAddresses.length || !emailOptions.attachmentFormat || (emailOptions.emailType === 'DAILY' && (!emailOptions.dailyFromDate || !emailOptions.dailyToDate))">
            <mat-icon>send</mat-icon> Send
          </button>
          <button mat-stroked-button (click)="toggleEmailOptions()">
            <mat-icon>close</mat-icon> Close
          </button>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .cq-page { padding: 16px; width: 100%; box-sizing: border-box; color: #1f2937; }
    .cq-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .cq-title { font-size: 20px; font-weight: 700; color: #0f1116; margin: 0 12px 0 0; }
    .cq-qno { font-size: 13px; font-weight: 600; color: #374151; background: #F3F4F6; padding: 2px 10px; border-radius: 8px; margin-right: 8px; }
    .cq-hdr-actions { display: flex; gap: 8px; }
    .cq-meta-card { padding: 20px; margin-bottom: 20px; }
    
    /* Responsive Grid for Header Form */
    .cq-meta-grid { 
      display: grid; 
      grid-template-columns: repeat(1, 1fr); 
      gap: 16px; 
      align-items: start; 
    }
    @media (min-width: 640px) {
      .cq-meta-grid { grid-template-columns: repeat(2, 1fr); }
    }
    @media (min-width: 1024px) {
      .cq-meta-grid { grid-template-columns: repeat(4, 1fr); }
    }
    .cq-col-span-1-to-2 { grid-column: span 1; }
    @media (min-width: 640px) {
       .cq-col-span-1-to-2 { grid-column: span 2; }
    }
    
    .cq-customer-row { display: flex; align-items: flex-start; gap: 8px; }
    .cq-customer-field { flex: 1; }
    mat-form-field { width: 100%; }
    
    .cq-action-btn-container { height: 100%; display: flex; flex-direction: column; justify-content: flex-start; }
    .cq-action-btn-container button { width: 100%; height: 56px; }

    .cq-rates-card { padding: 16px; margin-bottom: 20px; }
    @media (min-width: 640px) {
       .cq-rates-card { padding: 24px; }
    }
    
    .cq-doc-title {
      font-size: 14px; font-weight: 700; text-align: center;
      letter-spacing: 0.08em; text-transform: uppercase;
      margin-bottom: 20px; color: #0f1116;
      border-bottom: 2px solid #E5E7EB; padding-bottom: 12px;
    }
    .totals-section { background: #F9FAFB; padding: 12px; border-radius: 6px; margin-bottom: 16px; font-size: 14px; text-align: right; border: 1px solid #E5E7EB;}
    
    /* Responsive Table Wrapper */
    .cq-table-wrap { overflow-x: auto; margin-bottom: 4px; border: 1px solid #E5E7EB; border-radius: 4px; width: 100%; }
    
    .cq-table {
      width: 100%; border-collapse: collapse; table-layout: auto;
      font-size: 13px; font-family: 'Helvetica Neue', Arial, sans-serif;
      color: #1f2937;
    }
    .cq-th { border-bottom: 1px solid #9CA3AF; padding: 12px 8px; text-align: left; vertical-align: middle; font-weight: 600; line-height: 1.3; background: #F3F4F6; word-wrap: break-word; }
    
    .cq-td {
      border-bottom: 1px solid #E5E7EB; padding: 8px;
      vertical-align: top; word-wrap: break-word;
    }
    .cq-cell-text { white-space: normal; word-break: break-word; display: block; }
    .cq-truncate { white-space: normal; word-break: break-word; display: inline-block; }
    
    .cq-input {
      width: 100%; box-sizing: border-box;
      border: 1px solid #D1D5DB; border-radius: 4px; outline: none;
      font-size: 13px; padding: 6px 8px;
      background: white; color: #111827;
      font-family: inherit; min-width: 50px;
    }
    .cq-input:focus { border-color: #3B82F6; box-shadow: 0 0 0 1px #3B82F6; }
    .cq-cell-total { font-size: 11px; color: #059669; margin-top: 4px; }
    .cq-total-addl { display: flex; align-items: center; padding-top: 8px; }
    .cq-action-bar { display: flex; gap: 12px; flex-wrap: wrap; padding: 8px 0; }
    .cq-email-card { border: 1px solid #dbeafe; background: #f8fbff; }
    .cq-daily-selection {
      grid-column: 1 / -1;
      border: 1px solid #dbeafe;
      border-radius: 8px;
      background: #fff;
      padding: 12px;
    }
    .cq-daily-selection__head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
      flex-wrap: wrap;
    }
    .cq-daily-selection__title { font-weight: 600; color: #0f172a; }
    .cq-daily-selection__count { font-size: 12px; color: #475569; }
    .cq-daily-selection__empty {
      font-size: 13px;
      color: #64748b;
      padding: 8px 0;
    }
    .cq-daily-selection__table-wrap {
      max-height: 280px;
      overflow: auto;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
    }
    .cq-daily-selection__table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      min-width: 720px;
    }
    .cq-daily-selection__table th,
    .cq-daily-selection__table td {
      padding: 8px 10px;
      border-bottom: 1px solid #eef2ff;
      text-align: left;
      white-space: nowrap;
    }
    .cq-daily-selection__table th {
      position: sticky;
      top: 0;
      z-index: 1;
      background: #f8fafc;
      font-weight: 600;
    }
    .cq-tracking-link { color: #2563EB; text-decoration: none; font-weight: 500; }
    .cq-tracking-link:hover { color: #1D4ED8; text-decoration: underline; }
    .cq-copy-btn { width: 24px; height: 24px; line-height: 24px; padding: 0; color: #6B7280; vertical-align: middle; margin-left: 4px; }
    .cq-copy-btn:hover { color: #374151; background: #f3f4f6; }
    .cq-breakup-toolbar {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
      align-items: center;
      gap: 16px 20px;
      border-bottom: 2px solid #E5E7EB;
      padding-bottom: 16px;
      margin-bottom: 20px;
    }
    .cq-breakup-title {
      justify-self: start;
      text-align: left;
      margin: 0 !important;
      padding: 0 !important;
      border: none !important;
    }
    .cq-breakup-search {
      justify-self: center;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 12px;
      max-width: 100%;
    }
    .cq-breakup-exports {
      justify-self: end;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .cq-search-field-narrow { width: 200px; max-width: 100%; margin-bottom: -1.25em; }
    .cq-search-field-wide { width: min(100vw - 48px, 380px); min-width: 220px; margin-bottom: -1.25em; }
    @media (max-width: 1100px) {
      .cq-breakup-toolbar {
        grid-template-columns: 1fr;
        justify-items: stretch;
      }
      .cq-breakup-title { justify-self: start; }
      .cq-breakup-search { justify-self: center; width: 100%; }
      .cq-breakup-exports { justify-self: end; width: 100%; justify-content: flex-end; }
    }
    .cq-td-sno { cursor: pointer; text-align: center; }
    .cq-td-sno.cq-sno-editing { cursor: default; }
    .cq-sno-actions { display: flex; gap: 2px; justify-content: center; align-items: center; }
    .cq-sno-action-btn { width: 36px; height: 36px; padding: 0; }
    /* Plain number fields: hide browser stepper arrows */
    .cq-page input[type=number].cq-no-spinner::-webkit-outer-spin-button,
    .cq-page input[type=number].cq-no-spinner::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
    .cq-page input[type=number].cq-no-spinner { -moz-appearance: textfield; appearance: textfield; }
  `]
})
export class SmallClientEntryFormComponent implements OnInit {

  headerForm!: FormGroup;
  quotationId: string | null = null;
  lastUpdatedBy = '';
  quotation: SmallClientEntryQuotation | null = null;
  currentStatus: string | null = null;
  loading = false;

  entries: SmallClientEntry[] = [];
  searchQuery: string = '';
  selectedSearchFields: string[] = [];
  /** When opening from AWB "View details" (?entryId= / ?awb=), narrow the breakup to that shipment. */
  shipmentFocusEntryId: string | null = null;
  private suppressBreakupSearchChange = false;
  searchFields = [
    { value: 'date', label: 'Date' },
    { value: 'courierType', label: 'Courier' },
    { value: 'trackingNumber', label: 'AWB NO' },
    { value: 'destination', label: 'Destination' },
    { value: 'weight', label: 'Weight' },
    { value: 'cost', label: 'Courier Cost' },
    { value: 'itemType', label: 'Item' },
    { value: 'zone', label: 'Zone' },
    { value: 'rateType', label: 'Rate Type' },
    { value: 'description', label: 'Description' },
    { value: 'consignor', label: 'Consignor' },
    { value: 'status', label: 'Status' }
  ];

  months = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];

  zones: any[] = [];
  allZones: any[] = []; // Used for displaying zone names in the table for all rate types
  defaultAddress: string = '';

  newEntry: any = { entryDate: this.getToday(), rateType: 'EXPRESS_RATE' };
  editingIndex: number = -1;
  editingEntry: any = {};
  newEntryFieldErrors: { [key: string]: string } = {};
  filteredCustomers: any[] = [];
  customerSearchTerm: string = '';

  pincodeLoading: { [key: string]: boolean } = { 'new': false, 'edit': false };
  stateDisabled: { [key: string]: boolean } = { 'new': false, 'edit': false };
  areaDisabled: { [key: string]: boolean } = { 'new': false, 'edit': false };
  pincodeAreas: { [key: string]: string[] } = { 'new': [], 'edit': [] };
  private customerSearch$ = new Subject<string>();
  showEmailOptions = false;
  emailOptions: {
    emailType: string;
    attachmentFormat: string;
    fromDate: Date | string | null;
    toDate: Date | string | null;
    dailyFromDate: Date | string | null;
    dailyToDate: Date | string | null;
    specificDate: Date | string | null;
    includeAmountInShipmentBreakup: boolean;
    includeGst: boolean;
    includeFuel: boolean;
    includeWeight: boolean;
    toAddresses: string[];
  } = {
    emailType: 'MONTHLY',
    attachmentFormat: 'PDF',
    fromDate: null,
    toDate: null,
    dailyFromDate: null,
    dailyToDate: null,
    specificDate: null,
    includeAmountInShipmentBreakup: true,
    includeGst: true,
    includeFuel: true,
    includeWeight: true,
    toAddresses: []
  };

  includeGst = true;
  includeFuel = true;
  includeWeight = true;

  companySettings: { couriers: string[], items: string[], statuses: string[], email?: string } = {
    couriers: [], items: [], statuses: []
  };
  /** Case-insensitive autocomplete option lists (Monthly Courier Quotation). */
  filteredCouriersNew: string[] = [];
  filteredItemsNew: string[] = [];
  filteredStatusesNew: string[] = [];
  filteredCouriersEdit: string[] = [];
  filteredItemsEdit: string[] = [];
  filteredStatusesEdit: string[] = [];
  customerEmail: string = '';
  availableEmails: string[] = [];
  dailyEmailEntries: SmallClientEntry[] = [];
  selectedDailyEntryIds = new Set<string>();

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private companySettingsLists: CompanySettingsListsService,
    private pincodeLookup: PincodeLookupService,
    private service: SmallClientEntryQuotationService,
    private zoneConfigService: ZoneConfigurationService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private http: HttpClient,
    private location: Location,
    private dialog: MatDialog,
    private excelExportService: ExcelExportService
  ) { }

  ngOnInit() {
    this.headerForm = this.fb.group({
      customerId: [''],
      customerSearch: [''],
      customerName: [''],
      month: [this.getCurrentMonth(), Validators.required],
      year: [new Date().getFullYear(), Validators.required],
      title: [''],
      invoiceNumber: [''],
      invoiceDate: [null as Date | null],
      note: [''],
      fuelChargePercentage: [null as number | null],
      fovCharges: [null as number | null],
      gstPercentage: [null as number | null]
    });

    this.loadAllZones();
    this.loadZonesForRateType(this.newEntry.rateType || 'EXPRESS_RATE');
    this.loadCompanySettings();

    this.customerSearch$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(term => {
      if (term.length >= 2) {
        this.apiService.search<any>('/small-clients', term, 0, 20).subscribe({
          next: (res) => {
            this.filteredCustomers = (res.content || []).filter(
              (p: any) => !p.partyType || p.partyType === 'CUSTOMER' || p.partyType === 'BOTH'
            );
          },
          error: () => this.filteredCustomers = []
        });
      } else {
        this.filteredCustomers = [];
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.quotationId = id;
      this.loadQuotation(id);
    }
  }

  loadAllZones() {
    this.zoneConfigService.getActiveZones().subscribe({
      next: (res) => {
        this.allZones = (res || []).map((z: any) => ({ value: z.id, label: z.zoneName }));
      },
      error: () => { this.allZones = []; }
    });
  }

  getCurrentMonth(): string {
    const d = new Date();
    return this.months[d.getMonth()];
  }

  getToday(): string {
    const d = new Date();
    return d.toISOString();
  }

  loadCompanySettings() {
    this.companySettingsLists.getLists().subscribe({
      next: (settings) => {
        this.companySettings = {
          couriers: settings.couriers,
          items: settings.items,
          statuses: settings.statuses,
          email: settings.email
        };
        this.refreshCiDropdownLists();
      }
    });
  }

  private refreshCiDropdownLists(): void {
    const c = this.companySettings.couriers || [];
    const it = this.companySettings.items || [];
    const st = this.companySettings.statuses || [];
    this.filteredCouriersNew = [...c];
    this.filteredItemsNew = [...it];
    this.filteredStatusesNew = [...st];
    this.filteredCouriersEdit = [...c];
    this.filteredItemsEdit = [...it];
  }

  onCiFilterInput(ev: Event, kind: 'courier' | 'item' | 'status', mode: 'new' | 'edit'): void {
    const raw = (ev.target as HTMLInputElement).value ?? '';
    const needle = raw.toLowerCase();
    const filterList = (list: string[] | undefined) => {
      const a = list || [];
      return !needle ? [...a] : a.filter((x) => x.toLowerCase().includes(needle));
    };
    if (mode === 'new') {
      if (kind === 'courier') this.filteredCouriersNew = filterList(this.companySettings.couriers);
      else if (kind === 'item') this.filteredItemsNew = filterList(this.companySettings.items);
      else this.filteredStatusesNew = filterList(this.companySettings.statuses);
    } else {
      if (kind === 'courier') this.filteredCouriersEdit = filterList(this.companySettings.couriers);
      else if (kind === 'item') this.filteredItemsEdit = filterList(this.companySettings.items);
      else this.filteredStatusesEdit = filterList(this.companySettings.statuses);
    }
  }

  onCustomerInput(ev: Event) {
    if (this.quotationId) return;
    this.customerSearchTerm = (ev.target as HTMLInputElement).value;
    this.customerSearch$.next(this.customerSearchTerm);
  }

  onCustomerKeyUp(ev: KeyboardEvent) {
    if (this.quotationId) return;
    if (!(ev.target as HTMLInputElement).value) {
      this.headerForm.patchValue({ customerId: '', customerName: '' });
      this.filteredCustomers = [];
    }
  }

  onCustomerSelected(ev: any) {
    if (this.quotationId) return;
    const c = ev.option.value;
    this.headerForm.patchValue({ customerId: c.id, customerSearch: c.partyName, customerName: c.partyName });

    // Auto-populate for new shipments
    this.newEntry.consignor = c.partyName;
    // Construct basic address but do not auto-fill per User constraints
    this.defaultAddress = '';
    this.newEntry.receiverName = '';
  }

  onCustomerBlur() {
    if (this.quotationId) return;
    const val = this.headerForm.get('customerSearch')?.value;
    if (typeof val === 'string' && val.trim()) {
      // If user typed string and hasn't selected an object
      this.headerForm.patchValue({ customerId: null, customerName: val.trim() });
      if (!this.newEntry.consignor) {
        this.newEntry.consignor = val.trim();
      }
    } else if (val && typeof val === 'object' && val.partyName) {
      this.headerForm.patchValue({ customerId: val.id, customerName: val.partyName });
    }
  }

  customerDisplay(c: any | string): string {
    return typeof c === 'string' ? c : (c?.partyName || '');
  }

  loadQuotation(id: string) {
    this.loading = true;
    this.service.getById(id).subscribe({
      next: (q) => {
        this.quotation = q;
        this.lastUpdatedBy = q.lastUpdatedBy ?? '';
        const invDate = q.invoiceDate ? parseIsoDateToLocal(String(q.invoiceDate)) : null;
        this.headerForm.patchValue({
          customerId: q.customerId,
          customerSearch: q.customerName,
          customerName: q.customerName,
          month: q.month,
          year: q.year,
          title: q.title,
          invoiceNumber: q.invoiceNumber ?? '',
          invoiceDate: invDate,
          note: q.note ?? '',
          fuelChargePercentage: q.fuelChargePercentage ?? null,
          fovCharges: q.fovCharges ?? null,
          gstPercentage: q.gstPercentage ?? null
        });
        if (q.includeGst != null) this.includeGst = q.includeGst;
        if (q.includeFuel != null) this.includeFuel = q.includeFuel;

        // Auto-populate consignor with the selected small client name (same behavior as Client Entry).
        // Set synchronously so it is guaranteed regardless of the async party lookup timing.
        if (!this.newEntry.consignor || String(this.newEntry.consignor).trim() === '') {
          this.newEntry.consignor = q.customerName;
        }

        // Setup initial consignor info for new entries if not set
        this.apiService.get<any>('/parties/' + q.customerId).subscribe({
          next: (party) => {
            if (party.email) this.customerEmail = party.email;
            let addr = party.address || '';
            if (party.city) addr += (addr ? ', ' : '') + party.city;
            if (party.state) addr += (addr ? ', ' : '') + party.state;
            if (party.pincode) addr += (addr ? ' - ' : '') + party.pincode;
            this.defaultAddress = '';

            if (!this.newEntry.consignor || String(this.newEntry.consignor).trim() === '') {
              this.newEntry.consignor = q.customerName;
            }
            if (!this.newEntry.receiverName) {
              this.newEntry.receiverName = '';
            }
          },
          error: () => { }
        });

        this.loadEntries(id);
        this.loading = false;
      },
      error: () => { this.toastService.error('Error', 'Failed to load quotation'); this.loading = false; }
    });
  }

  loadEntries(id: string) {
    this.service.getEntries(id).subscribe({
      next: (res) => {
        this.entries = res;
        this.applyShipmentQueryFocus();
      },
      error: () => this.toastService.error('Error', 'Failed to load entries')
    });
  }

  /** Apply ?entryId= and ?awb= from route (e.g. dashboard AWB search â†’ View details). */
  private applyShipmentQueryFocus(): void {
    const qpm = this.route.snapshot.queryParamMap;
    const eid = (qpm.get('entryId') || '').trim();
    const awb = (qpm.get('awb') || '').trim();
    this.shipmentFocusEntryId = null;
    const hit = eid ? this.entries.find((e) => e.id === eid) : undefined;
    if (hit) {
      this.shipmentFocusEntryId = eid;
    }
    const awbText = awb || (hit?.trackingNumber ? String(hit.trackingNumber) : '');
    if (awbText) {
      this.suppressBreakupSearchChange = true;
      this.searchQuery = awbText.trim();
      this.selectedSearchFields = ['trackingNumber'];
      queueMicrotask(() => (this.suppressBreakupSearchChange = false));
    }
  }

  /** User edited the breakup search — drop single-row focus so the normal filter applies. */
  onBreakupSearchChange(_: string): void {
    if (this.suppressBreakupSearchChange) return;
    this.shipmentFocusEntryId = null;
  }

  clearShipmentBreakupFilter(): void {
    this.suppressBreakupSearchChange = true;
    this.searchQuery = '';
    this.shipmentFocusEntryId = null;
    this.selectedSearchFields = [];
    queueMicrotask(() => (this.suppressBreakupSearchChange = false));
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { awb: null, entryId: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  onPincodeChange(mode: 'new' | 'edit') {
    const targetObj = mode === 'new' ? this.newEntry : this.editingEntry;
    const pincode = targetObj.pincode;
    
    // Clear area & state if typing
    targetObj.state = '';
    targetObj.areaName = '';
    this.pincodeAreas[mode] = [];
    this.stateDisabled[mode] = false;
    this.areaDisabled[mode] = false;

    const pc = pincode != null ? String(pincode).trim() : '';
    if (/^\d{6}$/.test(pc)) {
      this.fetchPincodeDetails(pc, mode);
    }
  }

  fetchPincodeDetails(pincode: string, mode: 'new' | 'edit') {
    this.pincodeLoading[mode] = true;
    this.pincodeLookup.lookup(pincode).subscribe((out) => {
      this.pincodeLoading[mode] = false;
      const targetObj = mode === 'new' ? this.newEntry : this.editingEntry;
      if (this.pincodeLookup.isUnavailable(out)) {
        this.stateDisabled[mode] = false;
        this.areaDisabled[mode] = false;
        return;
      }
      if (out.kind !== 'success') {
        this.stateDisabled[mode] = false;
        this.areaDisabled[mode] = false;
        return;
      }
      targetObj.state = out.state;
      this.stateDisabled[mode] = true;
      this.pincodeAreas[mode] = out.areaNames;
      if (out.areaNames.length === 1) {
        targetObj.areaName = out.areaNames[0];
        this.areaDisabled[mode] = true;
      } else {
        targetObj.areaName = '';
        this.areaDisabled[mode] = false;
      }
    });
  }

  get filteredEntries(): SmallClientEntry[] {
    if (this.shipmentFocusEntryId) {
      const focused = this.entries.filter((e) => e.id === this.shipmentFocusEntryId);
      if (focused.length) {
        return focused;
      }
    }
    if (!this.searchQuery) return this.entries;
    const s = this.searchQuery.toLowerCase();
    const fields = this.selectedSearchFields && this.selectedSearchFields.length > 0 ? this.selectedSearchFields : this.searchFields.map(f => f.value);

    return this.entries.filter(e => {
       const dateStr = formatLocalDateOnly(e.entryDate as any) ?? '';
       const amt = this.getEntryTotal(e);
       const zoneLabel = this.getZoneLabel(e.zone);
       const rateLabel = this.getRateTypeLabel(e.rateType);
       
       let match = false;
       if (fields.includes('date') && dateStr.includes(s)) match = true;
       if (fields.includes('courierType') && e.courierType && e.courierType.toLowerCase().includes(s)) match = true;
       if (fields.includes('trackingNumber') && e.trackingNumber && e.trackingNumber.toLowerCase().includes(s)) match = true;
       if (fields.includes('destination') && e.consigneeAddress && e.consigneeAddress.toLowerCase().includes(s)) match = true;
       if (fields.includes('weight') && e.weight && e.weight.toString().includes(s)) match = true;
       if (fields.includes('cost') && amt.toString().includes(s)) match = true;
       if (fields.includes('itemType') && e.itemType && e.itemType.toLowerCase().includes(s)) match = true;
       if (fields.includes('zone') && zoneLabel && zoneLabel.toLowerCase().includes(s)) match = true;
       if (fields.includes('rateType') && rateLabel && rateLabel.toLowerCase().includes(s)) match = true;
       if (fields.includes('description') && e.additionalChargesDescription && e.additionalChargesDescription.toLowerCase().includes(s)) match = true;
       if (fields.includes('consignor') && e.consignor && e.consignor.toLowerCase().includes(s)) match = true;
       if (fields.includes('status') && e.deliveryStatus && e.deliveryStatus.toLowerCase().includes(s)) match = true;
       
       return match;
    });
  }

  copyAwb(awb: string) {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(awb).then(() => {
        this.toastService.success('Copied', 'AWB Number copied');
      });
    } else {
      // Fallback
      const textArea = document.createElement("textarea");
      textArea.value = awb;
      document.body.appendChild(textArea);
      textArea.select();
      try {
        document.execCommand('copy');
        this.toastService.success('Copied', 'AWB Number copied');
      } catch (err) {
        this.toastService.error('Error', 'Failed to copy');
      }
      document.body.removeChild(textArea);
    }
  }

  createQuotation() {
    if (this.headerForm.invalid) {
      const f = this.headerForm.controls;
      const missing: string[] = [];
      if (!f['customerId']?.value && !f['customerSearch']?.value && !f['customerName']?.value) missing.push('Customer');
      if (f['month']?.errors?.['required']) missing.push('Month');
      if (f['year']?.errors?.['required']) missing.push('Year');
      if (missing.length) {
        this.toastService.warning('Validation', 'Required: ' + missing.join(', '));
        return;
      }
    }
    this.loading = true;
    const v = this.headerForm.value;
    
    // Ensure customerName is extracted correctly whether it's an object or string
    let custName = v.customerName;
    if (!custName && v.customerSearch) {
      custName = typeof v.customerSearch === 'string' ? v.customerSearch.trim() : v.customerSearch.partyName;
    }

    this.service.create({
      customerId: v.customerId && v.customerId !== '' ? v.customerId : null,
      customerName: custName,
      month: v.month,
      year: v.year
    }).subscribe({
      next: (res) => {
        this.toastService.success('Success', 'Quotation draft created.');
        this.router.navigate(['/small-client-entries/edit', res.id]);
      },
      error: (err) => {
        this.toastService.error('Error', err?.error?.message || 'Creation failed');
        this.loading = false;
      }
    });
  }

  updateQuotation() {
    if (!this.quotationId) return;
    const v = this.headerForm.value;
    const gstRaw = v.gstPercentage;
    if (gstRaw != null && gstRaw !== '') {
      const gst = Number(gstRaw);
      if (gst < 0 || gst > 100) {
        this.toastService.warning('GST', 'GST percentage must be between 0 and 100');
        return;
      }
    }
    this.loading = true;
    let custName = v.customerName;
    if (!custName && v.customerSearch) {
      custName = typeof v.customerSearch === 'string' ? v.customerSearch.trim() : v.customerSearch.partyName;
    }
    this.service.update(this.quotationId, {
      customerId: v.customerId && v.customerId !== '' ? v.customerId : null,
      customerName: custName,
      month: v.month,
      year: v.year,
      title: v.title,
      invoiceNumber: v.invoiceNumber ?? undefined,
      invoiceDate: formatLocalDateOnly(v.invoiceDate),
      note: v.note ?? undefined,
      fuelChargePercentage: v.fuelChargePercentage != null && v.fuelChargePercentage !== '' ? Number(v.fuelChargePercentage) : null,
      fovCharges: v.fovCharges != null && v.fovCharges !== '' ? Number(v.fovCharges) : null,
      gstPercentage: v.gstPercentage != null && v.gstPercentage !== '' ? Number(v.gstPercentage) : null,
      includeGst: this.includeGst,
      includeFuel: this.includeFuel,
      includeFov: true
    }).subscribe({
      next: (res) => {
        this.quotation = res;
        this.lastUpdatedBy = res.lastUpdatedBy ?? this.lastUpdatedBy;
        this.toastService.success('Success', 'Updated');
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', err?.error?.message || 'Update failed');
        this.loading = false;
      }
    });
  }

  // ENTRY CRUD
  addEntry() {
    if (!this.quotationId) return;
    this.newEntryFieldErrors = {};
    const missing: string[] = [];
    if (!this.newEntry.entryDate) missing.push('Date');
    if (!this.newEntry.rateType) missing.push('Rate Type');
    if (!this.newEntry.zone) missing.push('Zone');
    if (!this.newEntry.consignor || String(this.newEntry.consignor).trim() === '') missing.push('Consignor');
    if (!this.newEntry.receiverName || String(this.newEntry.receiverName).trim() === '') missing.push('Receiver Name');
    if (!this.newEntry.courierType || String(this.newEntry.courierType).trim() === '') missing.push('Courier');
    if (this.newEntry.weight == null || this.newEntry.weight === '' || Number(this.newEntry.weight) <= 0) missing.push('Weight (Kg)');
    if (missing.length > 0) {
      this.newEntryFieldErrors['entryDate'] = !this.newEntry.entryDate ? 'Date is required' : '';
      this.newEntryFieldErrors['rateType'] = !this.newEntry.rateType ? 'Rate Type is required' : '';
      this.newEntryFieldErrors['zone'] = !this.newEntry.zone ? 'Zone is required' : '';
      this.newEntryFieldErrors['consignor'] = (!this.newEntry.consignor || String(this.newEntry.consignor).trim() === '') ? 'Consignor is required' : '';
      this.newEntryFieldErrors['receiverName'] = (!this.newEntry.receiverName || String(this.newEntry.receiverName).trim() === '') ? 'Receiver Name is required' : '';
      this.newEntryFieldErrors['pincode'] = '';
      this.newEntryFieldErrors['courierType'] = (!this.newEntry.courierType || String(this.newEntry.courierType).trim() === '') ? 'Courier is required' : '';
      this.newEntryFieldErrors['weight'] = (this.newEntry.weight == null || this.newEntry.weight === '' || Number(this.newEntry.weight) <= 0) ? 'Weight is required' : '';
      this.toastService.warning('Validation', 'Please fix the following: ' + missing.join(', '));
      return;
    }

    const payload = {
      ...this.newEntry,
      entryDate: this.toDateStr(this.newEntry.entryDate),
      monthlyQuotationId: this.quotationId,
      amountOverridden: Boolean(this.newEntry.amountOverridden)
    };

    this.service.addEntry(this.quotationId, payload).subscribe({
      next: (res) => {
        this.toastService.success('Added', 'Entry added successfully');
        this.newEntry = {
          entryDate: this.newEntry.entryDate,
          rateType: this.newEntry.rateType || 'EXPRESS_RATE',
          consignor: this.quotation?.customerName ?? '',
          receiverName: '',
          receiverPhoneNo: '',
          pincode: '',
          areaName: '',
          state: '',
          destinationCity: '',
          fullAddress: '',
          consigneeAddress: '',
          zone: this.newEntry.zone,
          amountOverridden: false,
          additionalCharges: null as number | null,
          additionalChargesDescription: ''
        };
        this.newEntryFieldErrors = {};
        this.refreshData();
      },
      error: (err) => {
        const msg = err?.error?.message || (typeof err?.error === 'string' ? err.error : 'Failed to add entry');
        this.toastService.error('Validation Error', msg);
      }
    });
  }

  clearNewEntryFieldError(field: string) {
    if (this.newEntryFieldErrors[field]) {
      this.newEntryFieldErrors = { ...this.newEntryFieldErrors, [field]: '' };
    }
  }

  onSnoCellClick(index: number, entry: SmallClientEntry) {
    if (this.editingIndex !== index) {
      this.startEdit(index, entry);
    }
  }

  startEdit(index: number, entry: SmallClientEntry) {
    this.editingIndex = index;
    const rt = entry.rateType || 'EXPRESS_RATE';
    const dt = entry.entryDate
      ? (parseIsoDateToLocal(String(entry.entryDate)) ?? new Date())
      : new Date();
    this.editingEntry = {
      ...entry,
      entryDate: dt as any,
      rateType: rt
    };
    this.refreshCiDropdownLists();
    this.loadZonesForRateType(rt);
  }

  cancelEdit() {
    this.editingIndex = -1;
    this.editingEntry = {};
    this.loadZonesForRateType(this.newEntry.rateType || 'EXPRESS_RATE');
  }

  onAmountEdit() {
    this.editingEntry.amountOverridden = true;
  }

  onNewAmountEdit() {
    this.newEntry.amountOverridden = true;
  }

  onNewRateTypeChange() {
    const rt = this.newEntry.rateType || 'EXPRESS_RATE';
    this.loadZonesForRateType(rt);
    if (!this.newEntry.amountOverridden) {
      this.calculateNewEntryAmount();
    }
  }

  onNewWeightChanged() {
    if (!this.newEntry.amountOverridden) {
      this.calculateNewEntryAmount();
    }
  }

  /** Load zones for the given rate type (Standard â†’ Standard zones only; Express/Surface â†’ non-Standard). */
  loadZonesForRateType(rateType: string) {
    this.zoneConfigService.getActiveZonesByRateType(rateType || 'EXPRESS_RATE').subscribe({
      next: (res) => {
        this.zones = (res || []).map((z: any) => ({ value: z.id, label: z.zoneName }));
        if (this.zones.length > 0 && (!this.newEntry.zone || !this.zones.find((z: any) => z.value === this.newEntry.zone))) {
          this.newEntry.zone = this.zones[0].value;
        }
        if (this.editingIndex >= 0 && this.editingEntry?.zone && !this.zones.find((z: any) => z.value === this.editingEntry.zone)) {
          this.editingEntry.zone = this.zones.length > 0 ? this.zones[0].value : '';
        }
        this.calculateNewEntryAmount();
        if (this.editingIndex >= 0) this.calculateEditEntryAmount();
      },
      error: () => { this.zones = []; }
    });
  }

  onEditRateTypeChange() {
    this.loadZonesForRateType(this.editingEntry?.rateType || 'EXPRESS_RATE');
  }

  onEditWeightChanged() {
    if (!this.editingEntry.amountOverridden) {
      this.calculateEditEntryAmount();
    }
  }

  calculateNewEntryAmount() {
    if (!this.quotationId || !this.newEntry.zone || !this.newEntry.weight) return;
    const rateType = this.newEntry.rateType || 'EXPRESS_RATE';
    this.service.calculateAmount(this.quotationId, this.newEntry.zone, rateType, this.newEntry.weight)
      .subscribe({
        next: (amt) => {
          if (!this.newEntry.amountOverridden) {
            this.newEntry.amount = amt;
          }
        }
      });
  }

  calculateEditEntryAmount() {
    if (!this.quotationId || !this.editingEntry.zone || !this.editingEntry.weight) return;
    const rateType = this.editingEntry.rateType || 'EXPRESS_RATE';
    this.service.calculateAmount(this.quotationId, this.editingEntry.zone, rateType, this.editingEntry.weight)
      .subscribe({
        next: (amt) => {
          if (!this.editingEntry.amountOverridden) {
            this.editingEntry.amount = amt;
          }
        }
      });
  }

  saveEdit() {
    if (!this.editingEntry.id || !this.quotationId) return;
    const payload = this.buildShipmentEntrySavePayload();
    if (!payload) return;
    this.service.updateEntry(this.editingEntry.id, payload).subscribe({
      next: (res) => {
        this.toastService.success('Updated', 'Entry updated successfully');
        const idx = this.entries.findIndex((x) => x.id === res.id);
        if (idx >= 0) {
          this.entries[idx] = { ...this.entries[idx], ...res };
        }
        this.editingIndex = -1;
        this.editingEntry = {};
        this.loadZonesForRateType(this.newEntry.rateType || 'EXPRESS_RATE');
        this.refreshData();
      },
      error: (err) => this.toastService.error('Error', err?.error?.message || 'Failed to update entry')
    });
  }

  /** Normalized body for PUT so Jackson validation passes (numbers as numbers, no stray audit fields). */
  private buildShipmentEntrySavePayload(): SmallClientEntry | null {
    const e = this.editingEntry;
    const w = Number(e.weight);
    if (Number.isNaN(w) || w <= 0) {
      this.toastService.warning('Validation', 'Weight must be a valid positive number');
      return null;
    }
    const zone = String(e.zone ?? '').trim();
    if (!zone) {
      this.toastService.warning('Validation', 'Zone is required');
      return null;
    }
    const entryDateStr = this.toDateStr(e.entryDate);
    if (!entryDateStr) {
      this.toastService.warning('Validation', 'Date is required');
      return null;
    }
    const addl = e.additionalCharges;
    const addlNum = addl != null && addl !== '' ? Number(addl) : undefined;
    const body: SmallClientEntry & { amountOverridden?: boolean } = {
      id: e.id,
      monthlyQuotationId: this.quotationId!,
      entryDate: entryDateStr as any,
      consignor: String(e.consignor ?? '').trim(),
      receiverName: String(e.receiverName ?? '').trim(),
      receiverPhoneNo: e.receiverPhoneNo != null ? String(e.receiverPhoneNo).trim() : '',
      pincode: e.pincode != null ? String(e.pincode).trim() : '',
      areaName: e.areaName != null ? String(e.areaName).trim() : '',
      state: e.state != null ? String(e.state).trim() : '',
      destinationCity: e.destinationCity != null ? String(e.destinationCity).trim() : '',
      fullAddress: e.fullAddress != null ? String(e.fullAddress).trim() : '',
      consigneeAddress: String(e.consigneeAddress ?? '').trim(),
      courierType: String(e.courierType ?? '').trim(),
      weight: w,
      trackingNumber: e.trackingNumber != null ? String(e.trackingNumber).trim() : '',
      itemType: e.itemType != null ? String(e.itemType).trim() : '',
      deliveryStatus: e.deliveryStatus != null ? String(e.deliveryStatus).trim() : '',
      zone,
      rateType: e.rateType || 'EXPRESS_RATE',
      amount: e.amount != null && e.amount !== '' ? Number(e.amount) : undefined,
      additionalCharges: addlNum != null && !Number.isNaN(addlNum) ? addlNum : undefined,
      additionalChargesDescription: e.additionalChargesDescription != null ? String(e.additionalChargesDescription) : ''
    };
    if (e.amountOverridden === true) {
      body.amountOverridden = true;
    } else if (e.amountOverridden === false) {
      body.amountOverridden = false;
    }
    return body;
  }

  deleteEntry(entryId: string) {
    if (!confirm('Delete this entry?')) return;
    this.service.deleteEntry(entryId).subscribe({
      next: () => {
        this.toastService.success('Deleted', 'Entry removed');
        this.refreshData();
      },
      error: (err) => this.toastService.error('Error', err?.error?.message || 'Failed to delete')
    });
  }

  refreshData() {
    if (this.quotationId) {
      this.loadEntries(this.quotationId);
      // Refresh parent quotation to get new totals
      this.service.getById(this.quotationId).subscribe(q => this.quotation = q);
    }
  }



  generateInvoice(): void {
    if (!this.quotationId) return;
    this.loading = true;

    const proceedGenerate = () => {
      this.service.generateInvoice(this.quotationId!).subscribe({
        next: (res) => {
          const wasAlreadyGenerated = Boolean(this.quotation?.invoiceGenerated || this.quotation?.invoiceNumber);
          this.quotation = res;
          this.headerForm.patchValue({
            invoiceNumber: res.invoiceNumber,
            invoiceDate: res.invoiceDate ? parseIsoDateToLocal(String(res.invoiceDate)) : null
          });
          this.loading = false;
          const msg = wasAlreadyGenerated
            ? `Invoice updated: ${res.invoiceNumber}`
            : `Invoice generated: ${res.invoiceNumber}`;
          this.toastService.success('Success', msg);
        },
        error: (err) => {
          this.loading = false;
          this.toastService.error('Error', err?.error?.message || 'Failed to generate invoice');
        }
      });
    };

    if (this.headerForm.dirty) {
      const v = this.headerForm.value;
      let custName = v.customerName;
      if (!custName && v.customerSearch) {
        custName = typeof v.customerSearch === 'string' ? v.customerSearch.trim() : v.customerSearch.partyName;
      }
      this.service.update(this.quotationId, {
        customerId: v.customerId && v.customerId !== '' ? v.customerId : null,
        customerName: custName,
        month: v.month,
        year: v.year,
        title: v.title,
        invoiceNumber: v.invoiceNumber ?? undefined,
        invoiceDate: formatLocalDateOnly(v.invoiceDate),
        note: v.note ?? undefined,
        fuelChargePercentage: v.fuelChargePercentage != null && v.fuelChargePercentage !== '' ? Number(v.fuelChargePercentage) : null,
        fovCharges: v.fovCharges != null && v.fovCharges !== '' ? Number(v.fovCharges) : null,
        gstPercentage: v.gstPercentage != null && v.gstPercentage !== '' ? Number(v.gstPercentage) : null,
        includeGst: this.includeGst,
        includeFuel: this.includeFuel,
        includeFov: true
      }).subscribe({
        next: () => {
          this.headerForm.markAsPristine();
          proceedGenerate();
        },
        error: () => proceedGenerate()
      });
    } else {
      proceedGenerate();
    }
  }

  downloadPdf() {
    const dialogRef = this.dialog.open(PdfConfirmDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
      disableClose: false,
      panelClass: 'pdf-download-dialog-panel'
    });

    dialogRef.afterClosed().subscribe(includeBreakup => {
      if (includeBreakup === undefined) return;
      this.toastService.info('Downloading', 'Generating PDF…');
      this.http.get(`${this.apiService.getBaseUrl()}/small-client-entries/${this.quotationId}/pdf?includeGst=${this.includeGst}&includeFuel=${this.includeFuel}&includeWeight=${this.includeWeight}&invoiceAction=DOWNLOAD${!includeBreakup ? '&includeBreakup=false' : ''}`,
        { responseType: 'blob', observe: 'response' }).subscribe({
          next: (res: any) => {
            const fallback = monthlyInvoiceDownloadFilename(this.monthlyFilenameParts(), 'pdf');
            const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
            const url = window.URL.createObjectURL(res.body);
            const a = document.createElement('a'); a.href = url;
            a.download = name;
            document.body.appendChild(a); a.click();
            document.body.removeChild(a); window.URL.revokeObjectURL(url);
            this.toastService.success('Success', 'PDF downloaded');
          },
          error: () => this.toastService.error('Error', 'Failed to download PDF')
        });
    });
  }

  viewInvoice(): void {
    this.openInvoicePdf('VIEW');
  }

  printInvoice(): void {
    this.openInvoicePdf('PRINT');
  }

  private openInvoicePdf(action: 'VIEW' | 'PRINT'): void {
    if (!this.quotationId) {
      return;
    }
    const endpoint = `${this.apiService.getBaseUrl()}/small-client-entries/${this.quotationId}/pdf?includeGst=${this.includeGst}&includeFuel=${this.includeFuel}&includeWeight=${this.includeWeight}&invoiceAction=${action}`;
    this.http.get(endpoint, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        if (action === 'PRINT') {
          const frame = document.createElement('iframe');
          frame.style.position = 'fixed';
          frame.style.right = '0';
          frame.style.bottom = '0';
          frame.style.width = '0';
          frame.style.height = '0';
          frame.style.border = '0';
          frame.src = url;
          frame.onload = () => {
            frame.contentWindow?.focus();
            frame.contentWindow?.print();
          };
          document.body.appendChild(frame);
          setTimeout(() => {
            document.body.removeChild(frame);
            window.URL.revokeObjectURL(url);
          }, 60000);
          this.toastService.success('Print', 'Invoice print dialog opened');
        } else {
          window.open(url, '_blank', 'noopener');
          setTimeout(() => window.URL.revokeObjectURL(url), 60000);
          this.toastService.success('View', 'Invoice preview opened');
        }
      },
      error: () => this.toastService.error('Error', action === 'PRINT' ? 'Failed to print invoice' : 'Failed to view invoice')
    });
  }

  downloadWord() {
    this.toastService.info('Downloading', 'Generating Word doc…');
    this.http.get(`${this.apiService.getBaseUrl()}/small-client-entries/${this.quotationId}/word?includeGst=${this.includeGst}&includeFuel=${this.includeFuel}&includeWeight=${this.includeWeight}`,
      { responseType: 'blob', observe: 'response' }).subscribe({
        next: (res: any) => {
          const fallback = monthlyInvoiceDownloadFilename(this.monthlyFilenameParts(), 'docx');
          const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
          const url = window.URL.createObjectURL(res.body);
          const a = document.createElement('a'); a.href = url;
          a.download = name;
          document.body.appendChild(a); a.click();
          document.body.removeChild(a); window.URL.revokeObjectURL(url);
          this.toastService.success('Success', 'Word doc downloaded');
        },
        error: () => this.toastService.error('Error', 'Failed to download Word doc')
      });
  }

  toggleEmailOptions() {
    this.showEmailOptions = !this.showEmailOptions;
    // When opening email panel, sync GST/Fuel from main checkboxes so email PDF matches download behaviour
    if (this.showEmailOptions) {
      this.emailOptions.includeGst = this.includeGst;
      this.emailOptions.includeFuel = this.includeFuel;
      this.emailOptions.includeWeight = this.includeWeight;
      
      this.availableEmails = [];
      if (this.customerEmail) {
        this.availableEmails.push(...this.customerEmail.split(',').map(e => e.trim()).filter(e => e));
      }
      this.availableEmails = [...new Set(this.availableEmails)];
      this.emailOptions.toAddresses = [...this.availableEmails];
      if (this.emailOptions.emailType === 'DAILY') {
        this.onDailyDateRangeChanged();
      }
    } else {
      this.dailyEmailEntries = [];
      this.selectedDailyEntryIds.clear();
    }
  }

  private toDateStr(v: Date | string | null): string | null {
    return formatLocalDateOnly(v) ?? null;
  }

  onEmailTypeChange(): void {
    if (this.emailOptions.emailType !== 'DAILY') {
      this.dailyEmailEntries = [];
      this.selectedDailyEntryIds.clear();
      return;
    }
    this.onDailyDateRangeChanged();
  }

  onDailyDateRangeChanged(): void {
    if (this.emailOptions.emailType !== 'DAILY') {
      return;
    }
    if (!this.emailOptions.dailyFromDate || !this.emailOptions.dailyToDate) {
      this.dailyEmailEntries = [];
      this.selectedDailyEntryIds.clear();
      return;
    }
    const from = this.toDateStr(this.emailOptions.dailyFromDate);
    const to = this.toDateStr(this.emailOptions.dailyToDate);
    if (!from || !to) {
      this.dailyEmailEntries = [];
      this.selectedDailyEntryIds.clear();
      return;
    }
    if (new Date(to) < new Date(from)) {
      this.dailyEmailEntries = [];
      this.selectedDailyEntryIds.clear();
      return;
    }
    this.fetchDailyEmailEntries(from, to);
  }

  private fetchDailyEmailEntries(from: string, to: string): void {
    if (!this.quotationId) {
      return;
    }
    this.service.getEntries(this.quotationId).subscribe({
      next: (allEntries) => {
        const rows = (allEntries || []).filter((e) => {
          const d = this.toDateStr(e.entryDate as any);
          if (!d) {
            return false;
          }
          return d >= from && d <= to;
        });
        this.dailyEmailEntries = rows;
        this.selectedDailyEntryIds = new Set(rows.map((e) => e.id).filter((id): id is string => !!id));
      },
      error: () => {
        this.dailyEmailEntries = [];
        this.selectedDailyEntryIds.clear();
        this.toastService.error('Error', 'Failed to load records for selected date range');
      }
    });
  }

  isAllDailySelected(): boolean {
    const selectable = this.dailyEmailEntries.filter((e) => !!e.id).length;
    return selectable > 0 && this.selectedDailyEntryIds.size === selectable;
  }

  isDailySelectionIndeterminate(): boolean {
    const selectable = this.dailyEmailEntries.filter((e) => !!e.id).length;
    return this.selectedDailyEntryIds.size > 0 && this.selectedDailyEntryIds.size < selectable;
  }

  toggleSelectAllDaily(checked: boolean): void {
    if (!checked) {
      this.selectedDailyEntryIds.clear();
      return;
    }
    this.selectedDailyEntryIds = new Set(this.dailyEmailEntries.map((e) => e.id).filter((id): id is string => !!id));
  }

  isDailyEntrySelected(entryId?: string): boolean {
    return !!entryId && this.selectedDailyEntryIds.has(entryId);
  }

  toggleDailyEntrySelection(entryId: string | undefined, checked: boolean): void {
    if (!entryId) {
      return;
    }
    if (checked) {
      this.selectedDailyEntryIds.add(entryId);
    } else {
      this.selectedDailyEntryIds.delete(entryId);
    }
  }

  get dailySelectedCount(): number {
    return this.selectedDailyEntryIds.size;
  }

  sendEmail() {
    if (!this.quotationId) return;
    if (!this.emailOptions.attachmentFormat) {
      this.toastService.warning('Validation', 'Please select attachment format (PDF or Excel).');
      return;
    }
    if (this.emailOptions.emailType === 'DAILY') {
      if (!this.emailOptions.dailyFromDate || !this.emailOptions.dailyToDate) {
        this.toastService.warning('Validation', 'Please select From Date and To Date for Daily email.');
        return;
      }
      const from = this.toDateStr(this.emailOptions.dailyFromDate)!;
      const to = this.toDateStr(this.emailOptions.dailyToDate)!;
      if (new Date(to) < new Date(from)) {
        this.toastService.warning('Validation', 'To Date cannot be before From Date.');
        return;
      }
      if (!this.dailyEmailEntries.length) {
        this.toastService.warning('Validation', 'No records found for selected date range.');
        return;
      }
      if (!this.selectedDailyEntryIds.size) {
        this.toastService.warning('Validation', 'Please select at least one record to send email');
        return;
      }
    }
    const payload: any = {
      emailType: this.emailOptions.emailType || 'MONTHLY',
      attachmentFormat: this.emailOptions.attachmentFormat,
      includeAmountInShipmentBreakup: this.emailOptions.includeAmountInShipmentBreakup,
      includeGst: this.emailOptions.includeGst,
      includeFuel: this.emailOptions.includeFuel,
      includeWeight: this.emailOptions.includeWeight,
      toAddresses: this.emailOptions.toAddresses
    };
    if (this.emailOptions.emailType === 'DAILY') {
      payload.fromDate = this.toDateStr(this.emailOptions.dailyFromDate);
      payload.toDate = this.toDateStr(this.emailOptions.dailyToDate);
      payload.selectedEntryIds = Array.from(this.selectedDailyEntryIds);
    } else {
      if (this.emailOptions.fromDate) payload.fromDate = this.toDateStr(this.emailOptions.fromDate);
      if (this.emailOptions.toDate) payload.toDate = this.toDateStr(this.emailOptions.toDate);
      if (this.emailOptions.specificDate) payload.specificDate = this.toDateStr(this.emailOptions.specificDate);
    }
    this.toastService.info('Sending', 'Sending email...');
    this.http.post<any>(`${this.apiService.getBaseUrl()}/small-client-entries/${this.quotationId}/email`, payload)
      .subscribe({
        next: (res: any) => {
          this.toastService.success('Success', res?.message || 'Email sent successfully');
          this.showEmailOptions = false;
        },
        error: (err: any) => {
          this.toastService.error('Error', err?.error?.message || 'Failed to send email');
        }
      });
  }

  goBack() {
    this.location.back();
  }

  getZoneLabel(zoneId: string | undefined): string {
    if (!zoneId) return '-';
    // Use allZones to lookup label so it resolves correctly regardless of current filtered zones
    const z = this.allZones.find((x: any) => x.value === zoneId) || this.zones.find((x: any) => x.value === zoneId);
    return z ? z.label : zoneId;
  }

  getRateTypeLabel(rateType: string | undefined): string {
    if (!rateType) return '-';
    const labels: Record<string, string> = {
      STANDARD: 'Standard',
      EXPRESS_RATE: 'Express Rate',
      SURFACE_RATE: 'Surface Rate',
      SafetyPlus: 'Surface',
      PriorityClass: 'Safety(Priority)'
    };
    return labels[rateType] || rateType;
  }

  /** Total for display: base amount + additional charges */
  getEntryTotal(entry: SmallClientEntry | any): number {
    const base = entry?.amount != null ? Number(entry.amount) : 0;
    const addl = entry?.additionalCharges != null ? Number(entry.additionalCharges) : 0;
    return base + addl;
  }

  /** Total for new entry (amount + additional charges) */
  getNewEntryTotal(): number {
    const base = this.newEntry?.amount != null ? Number(this.newEntry.amount) : 0;
    const addl = this.newEntry?.additionalCharges != null ? Number(this.newEntry.additionalCharges) : 0;
    return base + addl;
  }

  private buildBreakupExportRows(): Record<string, string | number>[] {
    return this.filteredEntries.map((e, idx) => ({
      sno: idx + 1,
      entryDate: formatLocalDateOnly(e.entryDate as any) ?? '',
      courierType: e.courierType ?? '',
      trackingNumber: e.trackingNumber ?? '',
      consigneeAddress: e.consigneeAddress ?? '',
      weight: e.weight ?? '',
      itemType: e.itemType ?? '',
      zone: this.getZoneLabel(e.zone),
      rateType: this.getRateTypeLabel(e.rateType),
      description: e.additionalChargesDescription ?? '',
      consignor: e.consignor ?? '',
      status: e.deliveryStatus ?? '',
      additionalCharges: e.additionalCharges ?? '',
      courierCost: this.getEntryTotal(e)
    }));
  }

  async exportFilteredBreakupExcel(): Promise<void> {
    const rows = this.buildBreakupExportRows();
    if (!rows.length) {
      this.toastService.warning('Export', 'No rows match the current search.');
      return;
    }
    const headers = ['S.No', 'Date', 'Courier', 'AWB NO', 'Destination', 'Weight', 'Item', 'Zone', 'Rate Type', 'Description', 'Consignor', 'Status', 'Addl Chgs', 'Courier Cost'];
    try {
      const xlsxName = monthlyInvoiceDownloadFilename(this.monthlyFilenameParts(), 'xlsx');
      await this.excelExportService.exportToExcel(rows, xlsxName, headers, { exactFilename: true });
      this.toastService.success('Success', 'Filtered breakup exported to Excel');
    } catch {
      this.toastService.error('Error', 'Excel export failed');
    }
  }

  exportFilteredBreakupPdf(): void {
    if (!this.quotationId) return;
    const ids = this.filteredEntries.map(e => e.id).filter((id): id is string => !!id);
    if (!ids.length) {
      this.toastService.warning('Export', 'No rows match the current search.');
      return;
    }
    this.toastService.info('Export', 'Generating PDF…');
    const entryIds = encodeURIComponent(ids.join(','));
    const url = `${this.apiService.getBaseUrl()}/small-client-entries/${this.quotationId}/breakup-pdf?entryIds=${entryIds}&includeAmount=true&includeWeight=true`;
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        const fallback = monthlyBreakupPdfFallback(this.monthlyFilenameParts());
        const fname = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
        const blobUrl = window.URL.createObjectURL(res.body);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = fname;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(blobUrl);
        this.toastService.success('Success', 'Filtered breakup PDF downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to export PDF')
    });
  }

  private monthlyFilenameParts(): { customerName?: string | null; month?: string | null; year?: number | null } {
    const hf = this.headerForm?.value;
    return {
      customerName: this.quotation?.customerName ?? hf?.customerName,
      month: this.quotation?.month ?? hf?.month,
      year: this.quotation?.year ?? hf?.year
    };
  }
}

