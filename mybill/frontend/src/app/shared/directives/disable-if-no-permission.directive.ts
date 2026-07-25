import { Directive, ElementRef, Input, OnInit, Renderer2 } from '@angular/core';
import { PermissionService } from '../../core/services/permission.service';

const ACTION_LABELS: Record<string, string> = {
  view: 'View',
  create: 'Create',
  edit: 'Edit',
  delete: 'Delete',
  download: 'Download',
  export: 'Export',
  print: 'Print',
  email: 'Email'
};

/**
 * Attribute directive that disables the host element and shows a tooltip when
 * the current user lacks the specified permission.
 *
 * Usage:
 *   <button [appDisableIfNoPermission]="'CLIENTS:edit'" mat-icon-button>Edit</button>
 */
@Directive({
  selector: '[appDisableIfNoPermission]'
})
export class DisableIfNoPermissionDirective implements OnInit {
  private permissionInput: string | null = null;

  @Input()
  set appDisableIfNoPermission(value: string | null) {
    this.permissionInput = value;
    this.updateState();
  }

  constructor(
    private el: ElementRef<HTMLElement>,
    private renderer: Renderer2,
    private permissionService: PermissionService
  ) {}

  ngOnInit(): void {
    this.updateState();
  }

  private updateState(): void {
    if (!this.permissionInput) return;

    const parts = this.permissionInput.split(':');
    const moduleKey = parts[0];
    const action = parts.length > 1 ? parts[1] : 'view';
    const allowed = this.permissionService.hasPermission(moduleKey, action);

    if (!allowed) {
      this.renderer.setAttribute(this.el.nativeElement, 'disabled', 'true');
      this.renderer.setStyle(this.el.nativeElement, 'pointer-events', 'auto');
      this.renderer.setStyle(this.el.nativeElement, 'opacity', '0.5');
      const label = ACTION_LABELS[action] || action;
      this.renderer.setAttribute(
        this.el.nativeElement,
        'matTooltip',
        `${label} access is not assigned to your role.`
      );
      this.renderer.setAttribute(this.el.nativeElement, 'title',
        `${label} access is not assigned to your role.`
      );
    }
  }
}
