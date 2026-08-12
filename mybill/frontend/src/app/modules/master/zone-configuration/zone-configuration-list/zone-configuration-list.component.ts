import { Component, OnInit, ViewChild } from '@angular/core';
import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { ZoneConfiguration } from '../../../../core/models/zone-configuration.model';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
    selector: 'app-zone-configuration-list',
    templateUrl: './zone-configuration-list.component.html'
})
export class ZoneConfigurationListComponent implements OnInit {
    zones: ZoneConfiguration[] = [];
    displayedColumns: string[] = ['zoneName', 'zoneType', 'expressBaseWeight', 'expressIncrementalWeight', 'expressPerKgThreshold', 'isActive', 'actions'];
    totalElements = 0;
    pageSize = 10;
    pageIndex = 0;
    isLoading = false;
    searchTerm = '';
    private searchSubject = new Subject<string>();

    @ViewChild(MatPaginator) paginator!: MatPaginator;

    constructor(
        private zoneService: ZoneConfigurationService,
        private snackBar: MatSnackBar
    ) {
        this.searchSubject.pipe(
            debounceTime(500),
            distinctUntilChanged()
        ).subscribe(term => {
            this.searchTerm = term;
            this.pageIndex = 0;
            this.loadZones();
        });
    }

    ngOnInit(): void {
        this.loadZones();
    }

    loadZones(): void {
        this.isLoading = true;
        if (this.searchTerm) {
            this.zoneService.search(this.searchTerm, this.pageIndex, this.pageSize)
                .subscribe({
                    next: (res) => {
                        this.zones = res?.content ?? [];
                        this.totalElements = res?.totalElements ?? 0;
                        this.isLoading = false;
                    },
                    error: () => { this.zones = []; this.totalElements = 0; this.isLoading = false; }
                });
        } else {
            this.zoneService.getAll(this.pageIndex, this.pageSize)
                .subscribe({
                    next: (res) => {
                        this.zones = res?.content ?? [];
                        this.totalElements = res?.totalElements ?? 0;
                        this.isLoading = false;
                    },
                    error: () => { this.zones = []; this.totalElements = 0; this.isLoading = false; }
                });
        }
    }

    onSearch(event: any): void {
        this.searchSubject.next(event.target.value);
    }

    onPageChange(event: PageEvent): void {
        this.pageIndex = event.pageIndex;
        this.pageSize = event.pageSize;
        this.loadZones();
    }

    getZoneTypeLabel(zoneType: string | undefined): string {
        if (!zoneType) return '';
        if (zoneType === 'EXPRESS_SURFACE') return 'Express / Surface';
        if (zoneType === 'PRIORITY_SAFETY') return 'Safety(Priority) / Surface';
        if (zoneType === 'STANDARD') return 'Standard';
        return zoneType;
    }

    deleteZone(id: string | undefined): void {
        if (id && confirm('Are you sure you want to delete this zone configuration?')) {
            this.zoneService.delete(id).subscribe({
                next: () => {
                    this.snackBar.open('Zone deleted successfully', 'Close', { duration: 3000 });
                    this.loadZones();
                },
                error: (err) => {
                    this.snackBar.open(err.error?.message || 'Error deleting zone', 'Close', { duration: 3000, panelClass: ['error-snackbar'] });
                }
            });
        }
    }
}
