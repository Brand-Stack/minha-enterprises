import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { AnimationService } from '../../../../core/services/animation.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

interface Item {
  id: string;
  itemCode: string;
  itemName: string;
  sellingPrice: number;
  taxRate: number;
  stockQuantity: number;
}

@Component({
  selector: 'app-item-form',
  template: `
    <div class="page-container" #pageContainer>
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="w-full" #card>
        <mat-card-header>
          <mat-card-title class="dark:text-gray-100">{{ isEdit ? 'Edit' : 'Create' }} Item</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="itemForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Basic Information Section -->
            <div class="form-section">
              <h3 class="section-title">Basic Information</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Item Code</mat-label>
                  <input matInput formControlName="itemCode" [readonly]="true" [disabled]="true" placeholder="Auto-generated">
                  <mat-hint>Auto-generated - cannot be edited</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>EN Code</mat-label>
                  <input matInput formControlName="enCode" placeholder="Enter EN Code for item identification">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Item Name *</mat-label>
                  <input matInput formControlName="itemName" required>
                  <mat-error *ngIf="itemForm.get('itemName')?.hasError('required')">Name is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Category</mat-label>
                  <mat-select formControlName="category">
                    <mat-option *ngFor="let cat of categories" [value]="cat.name">{{ cat.name }}</mat-option>
                  </mat-select>
                  <mat-hint>Select from Master Data (Item Categories)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Unit</mat-label>
                  <mat-select formControlName="unit">
                    <mat-option *ngFor="let unit of units" [value]="unit.name">{{ unit.name }}</mat-option>
                  </mat-select>
                  <mat-hint>Select from Master Data (Item Units)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>HSN Code</mat-label>
                  <input matInput formControlName="hsnCode">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Tax Rate (%)</mat-label>
                  <input matInput type="number" formControlName="taxRate" step="0.01">
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Description</mat-label>
                <textarea matInput formControlName="description" rows="3"></textarea>
              </mat-form-field>
            </div>

            <!-- Pricing & Stock Section -->
            <div class="form-section">
              <h3 class="section-title">Pricing & Stock</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Purchase Price</mat-label>
                  <input matInput type="number" formControlName="purchasePrice" step="0.01">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Selling Price *</mat-label>
                  <input matInput type="number" formControlName="sellingPrice" step="0.01" required>
                  <mat-error *ngIf="itemForm.get('sellingPrice')?.hasError('required')">Selling price is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Stock Quantity</mat-label>
                  <input matInput type="number" formControlName="stockQuantity" step="1" min="0">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Min Stock Level</mat-label>
                  <input matInput type="number" formControlName="minStockLevel" step="1" min="0">
                </mat-form-field>
              </div>
            </div>

            <!-- Custom Fields Section -->
            <div class="form-section">
              <div class="flex justify-between items-center mb-4">
                <h3 class="section-title">Custom Fields</h3>
                <button type="button" mat-button (click)="addCustomField()" class="btn-secondary">
                  <mat-icon>add</mat-icon>
                  <span>Add Field</span>
                </button>
              </div>
              <div formArrayName="customFields">
                <div *ngFor="let field of customFieldsArray.controls; let i = index" 
                     [formGroupName]="i" 
                     class="custom-field-row">
                  <mat-form-field appearance="outline" class="flex-1">
                    <mat-label>Field Name</mat-label>
                    <input matInput formControlName="key" placeholder="e.g., Brand, Size, Color">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex-1">
                    <mat-label>Field Value</mat-label>
                    <input matInput formControlName="value" placeholder="Enter value">
                  </mat-form-field>
                  <button type="button" mat-icon-button (click)="removeCustomField(i)" class="btn-icon-danger">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </div>
              <p *ngIf="customFieldsArray.length === 0" class="text-neutral-light text-sm">
                Add custom fields to store additional item information
              </p>
            </div>

            <!-- Actions -->
            <app-last-updated-by-field *ngIf="isEdit" [value]="lastUpdatedBy"></app-last-updated-by-field>

            <div class="flex gap-4 pt-4">
              <button mat-raised-button color="primary" type="submit" 
                      [disabled]="itemForm.invalid || loading"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">{{ isEdit ? 'Update' : 'Create' }}</span>
                <span *ngIf="loading">Processing...</span>
              </button>
              <button mat-button type="button" (click)="cancel()">Cancel</button>
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

    .custom-field-row {
      display: flex;
      gap: 12px;
      align-items: center;
      margin-bottom: 12px;
    }

    .text-neutral-light {
      color: #6B7280;
    }
  `]
})
export class ItemFormComponent implements OnInit, AfterViewInit {
  itemForm: FormGroup;
  isEdit = false;
  lastUpdatedBy = '';
  itemId: string | null = null;
  loading = false;
  returnUrl: string | null = null;
  itemIndex: number | null = null;

  @ViewChild('pageContainer') pageContainer!: ElementRef;
  @ViewChild('card') card!: ElementRef;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private animationService: AnimationService,
    private toastService: ToastService
  ) {
    this.itemForm = this.fb.group({
      itemCode: [''], // Auto-generated, not required from user
      enCode: [''],
      itemName: ['', Validators.required],
      description: [''],
      category: [''],
      unit: [''],
      purchasePrice: [null],
      sellingPrice: [null, Validators.required],
      stockQuantity: [null],
      minStockLevel: [null],
      hsnCode: [''],
      taxRate: [null],
      customFields: this.fb.array([])
    });
  }
  
  categories: any[] = [];
  units: any[] = [];

  get customFieldsArray(): FormArray {
    return this.itemForm.get('customFields') as FormArray;
  }

  ngOnInit() {
    this.loadMasterData();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.itemId = id;
      this.loadItem(id);
    } else {
      // Check for query params (coming from billing form)
      this.route.queryParams.subscribe(params => {
        if (params['name']) {
          // Pre-fill item name if provided
          this.itemForm.patchValue({
            itemName: params['name']
          });
        }
        if (params['returnUrl']) {
          this.returnUrl = params['returnUrl'];
        }
        if (params['itemIndex']) {
          this.itemIndex = parseInt(params['itemIndex'], 10);
        }
      });
    }
  }
  
  loadMasterData() {
    // Load Item Categories
    this.apiService.get<any[]>('/master-data/type/ITEM_CATEGORY').subscribe({
      next: (data) => {
        this.categories = data;
      },
      error: (err) => {
        console.error('Failed to load categories', err);
      }
    });
    
    // Load Item Units
    this.apiService.get<any[]>('/master-data/type/ITEM_UNIT').subscribe({
      next: (data) => {
        this.units = data;
      },
      error: (err) => {
        console.error('Failed to load units', err);
      }
    });
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.animationService.fadeIn(this.pageContainer.nativeElement);
      this.animationService.scaleIn(this.card.nativeElement, 0.5);
    }, 100);
  }

  loadItem(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/items/${id}`).subscribe({
      next: (item) => {
        this.lastUpdatedBy = item.lastUpdatedBy ?? '';
        this.itemForm.patchValue({
          itemCode: item.itemCode,
          enCode: item.enCode || '',
          itemName: item.itemName,
          description: item.description,
          category: item.category,
          unit: item.unit,
          purchasePrice: item.purchasePrice,
          sellingPrice: item.sellingPrice,
          stockQuantity: item.stockQuantity,
          minStockLevel: item.minStockLevel,
          hsnCode: item.hsnCode,
          taxRate: item.taxRate
        });

        // Load custom fields
        if (item.customFields) {
          Object.keys(item.customFields).forEach(key => {
            this.addCustomField(key, item.customFields[key]);
          });
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load item');
        this.router.navigate(['/master/items']);
      }
    });
  }

  addCustomField(key: string = '', value: any = '') {
    const fieldGroup = this.fb.group({
      key: [key],
      value: [value]
    });
    this.customFieldsArray.push(fieldGroup);
  }

  removeCustomField(index: number) {
    this.customFieldsArray.removeAt(index);
  }

  onSubmit() {
    if (this.itemForm.valid) {
      this.loading = true;
      const formValue = this.itemForm.value;
      
      // Convert custom fields array to object
      const customFields: { [key: string]: any } = {};
      formValue.customFields.forEach((field: any) => {
        if (field.key && field.value) {
          customFields[field.key] = field.value;
        }
      });
      
      const item = {
        ...formValue,
        customFields: Object.keys(customFields).length > 0 ? customFields : undefined
      };
      
      // Remove itemCode if empty (let backend auto-generate it)
      if (!item.itemCode || item.itemCode.trim() === '') {
        delete item.itemCode;
      }
      
      const request = this.isEdit && this.itemId
        ? this.apiService.put<Item>('/items', this.itemId, item)
        : this.apiService.post<Item>('/items', item);
      
      request.subscribe({
        next: (createdItem: Item) => {
          this.loading = false;
          this.toastService.success('Success', `Item ${this.isEdit ? 'updated' : 'created'} successfully`);
          
          // If returning from billing form, navigate back with itemId
          if (this.returnUrl && !this.isEdit && createdItem?.id) {
            const returnUrl = new URL(this.returnUrl, window.location.origin);
            returnUrl.searchParams.set('itemId', createdItem.id);
            if (this.itemIndex !== null) {
              returnUrl.searchParams.set('itemIndex', this.itemIndex.toString());
            }
            setTimeout(() => {
              this.router.navigateByUrl(returnUrl.pathname + returnUrl.search);
            }, 500);
          } else {
            setTimeout(() => {
              this.router.navigate(['/master/items']);
            }, 500);
          }
        },
        error: () => {
          this.loading = false;
          this.toastService.error('Error', `Failed to ${this.isEdit ? 'update' : 'create'} item`);
        }
      });
    }
  }

  cancel() {
    // If coming from billing form, go back to billing
    if (this.returnUrl) {
      this.router.navigateByUrl(this.returnUrl);
      return;
    }
    this.router.navigate(['/master/items']);
  }
}

