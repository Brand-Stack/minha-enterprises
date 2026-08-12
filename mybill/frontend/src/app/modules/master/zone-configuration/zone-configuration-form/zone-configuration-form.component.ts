import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
    selector: 'app-zone-configuration-form',
    templateUrl: './zone-configuration-form.component.html'
})
export class ZoneConfigurationFormComponent implements OnInit {
    zoneForm!: FormGroup;
    isEditMode = false;
    zoneId: string | null = null;
    lastUpdatedBy = '';
    isLoading = false;
    isSaving = false;

    zoneTypes = [
        { value: 'EXPRESS_SURFACE', label: 'Express / Surface' },
        { value: 'PRIORITY_SAFETY', label: 'Safety(Priority) / Surface' },
        { value: 'STANDARD', label: 'Standard' }
    ];

    constructor(
        private fb: FormBuilder,
        private route: ActivatedRoute,
        private router: Router,
        private zoneService: ZoneConfigurationService,
        private snackBar: MatSnackBar
    ) { }

    ngOnInit(): void {
        this.initForm();
        this.route.paramMap.subscribe(params => {
            this.zoneId = params.get('id');
            if (this.zoneId) {
                this.isEditMode = true;
                this.loadZone();
            }
        });
    }

    initForm(): void {
        this.zoneForm = this.fb.group({
            zoneName: ['', [Validators.required, Validators.maxLength(100)]],
            zoneType: ['EXPRESS_SURFACE', Validators.required],

            expressBaseWeight: [0.250, [Validators.required, Validators.min(0)]],
            expressIncrementalWeight: [0.500, [Validators.required, Validators.min(0)]],
            expressPerKgThreshold: [3.0, [Validators.required, Validators.min(0)]],

            surfaceSlab1Threshold: [10.0, [Validators.required, Validators.min(0)]],
            surfaceSlab1Max: [200.0, [Validators.required, Validators.min(0)]],
            surfaceSlab2Threshold: [200.0, [Validators.required, Validators.min(0)]],
            surfaceSlab2Max: [500.0, [Validators.required, Validators.min(0)]],

            isActive: [true]
        });
    }

    loadZone(): void {
        if (!this.zoneId) return;
        this.isLoading = true;
        this.zoneService.getById(this.zoneId).subscribe({
            next: (zone) => {
                this.zoneForm.patchValue(zone);
                this.lastUpdatedBy = zone.lastUpdatedBy ?? '';
                this.isLoading = false;
            },
            error: (err) => {
                this.snackBar.open(err.error?.message || 'Error loading zone configuration', 'Close', { duration: 3000, panelClass: ['error-snackbar'] });
                this.isLoading = false;
                this.goBack();
            }
        });
    }

    onSubmit(): void {
        if (this.zoneForm.invalid) {
            this.zoneForm.markAllAsTouched();
            return;
        }

        this.isSaving = true;
        const zoneData = this.zoneForm.value;

        if (this.isEditMode && this.zoneId) {
            this.zoneService.update(this.zoneId, zoneData).subscribe({
                next: () => {
                    this.snackBar.open('Zone configuration updated successfully', 'Close', { duration: 3000 });
                    this.isSaving = false;
                    this.goBack();
                },
                error: (err) => {
                    this.snackBar.open(err.error?.message || 'Error updating zone', 'Close', { duration: 3000, panelClass: ['error-snackbar'] });
                    this.isSaving = false;
                }
            });
        } else {
            this.zoneService.create(zoneData).subscribe({
                next: () => {
                    this.snackBar.open('Zone configuration created successfully', 'Close', { duration: 3000 });
                    this.isSaving = false;
                    this.goBack();
                },
                error: (err) => {
                    this.snackBar.open(err.error?.message || 'Error creating zone', 'Close', { duration: 3000, panelClass: ['error-snackbar'] });
                    this.isSaving = false;
                }
            });
        }
    }

    goBack(): void {
        this.router.navigate(['/master/zone-configurations']);
    }
}
