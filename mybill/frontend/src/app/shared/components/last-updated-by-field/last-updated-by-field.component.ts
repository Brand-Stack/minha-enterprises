import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-last-updated-by-field',
  template: `
    <mat-form-field appearance="outline" class="last-updated-by-field" *ngIf="show">
      <mat-label>Last Updated By</mat-label>
      <input matInput [value]="value || '—'" readonly tabindex="-1" />
    </mat-form-field>
  `,
  styles: [`
    .last-updated-by-field {
      width: 100%;
    }
  `]
})
export class LastUpdatedByFieldComponent {
  @Input() value: string | null | undefined;
  @Input() show = true;
}
