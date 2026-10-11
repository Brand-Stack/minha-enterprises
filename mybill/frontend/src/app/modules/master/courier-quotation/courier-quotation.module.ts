import { NgModule } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { SharedModule } from '../../../shared/shared.module';
import { CourierQuotationListComponent } from './courier-quotation-list/courier-quotation-list.component';
import { CourierQuotationFormComponent } from './courier-quotation-form/courier-quotation-form.component';
import { Directive, ElementRef, HostListener } from '@angular/core';

@Directive({
    selector: 'input.cq-input'
})
export class NumericRateDirective {
    constructor(private el: ElementRef) { }
    @HostListener('input', ['$event']) onInputChange(event: Event) {
        const initialValue = this.el.nativeElement.value;
        const clean = initialValue.replace(/[^0-9.]/g, '');
        if (initialValue !== clean) {
            this.el.nativeElement.value = clean;
            event.stopPropagation();
        }
    }
}

@NgModule({
    declarations: [CourierQuotationListComponent, CourierQuotationFormComponent, NumericRateDirective],
    providers: [DatePipe],
    imports: [
        CommonModule,
        ReactiveFormsModule,
        FormsModule,
        RouterModule.forChild([
            { path: '', component: CourierQuotationListComponent },
            { path: 'create', component: CourierQuotationFormComponent },
            { path: 'edit/:id', component: CourierQuotationFormComponent }
        ]),
        MatTableModule, MatButtonModule, MatFormFieldModule, MatInputModule,
        MatPaginatorModule, MatIconModule, MatCardModule, MatTooltipModule,
        MatSelectModule, MatDatepickerModule, MatNativeDateModule,
        MatAutocompleteModule, MatTabsModule, MatChipsModule, SharedModule
    ]
})
export class CourierQuotationModule { }
