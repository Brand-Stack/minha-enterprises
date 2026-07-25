import { Directive, Input, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { PermissionService } from '../../core/services/permission.service';

/**
 * Structural directive that conditionally renders an element based on the current
 * user's entitlements. Removes the host element from the DOM when the permission
 * is not granted.
 *
 * <p>Usage:</p>
 * <pre>
 *   &lt;button *appHasPermission="'CLIENTS:create'"&gt;Add Client&lt;/button&gt;
 *   &lt;button *appHasPermission="{ module: 'CLIENTS', action: 'delete' }"&gt;Delete&lt;/button&gt;
 * </pre>
 *
 * <p>This is presentation sugar only; the backend independently enforces access.</p>
 */
@Directive({
  selector: '[appHasPermission]'
})
export class HasPermissionDirective implements OnInit {
  private permissionInput: string | { module: string; action?: string } | null = null;
  private rendered = false;

  @Input()
  set appHasPermission(value: string | { module: string; action?: string } | null) {
    this.permissionInput = value;
    this.updateView();
  }

  constructor(
    private templateRef: TemplateRef<unknown>,
    private viewContainer: ViewContainerRef,
    private permissionService: PermissionService
  ) {}

  ngOnInit(): void {
    this.updateView();
  }

  private updateView(): void {
    const allowed = this.evaluate();
    if (allowed && !this.rendered) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.rendered = true;
    } else if (!allowed && this.rendered) {
      this.viewContainer.clear();
      this.rendered = false;
    }
  }

  private evaluate(): boolean {
    if (!this.permissionInput) {
      return false;
    }

    let moduleKey: string;
    let action = 'view';

    if (typeof this.permissionInput === 'string') {
      // Supports "MODULE:action" or just "MODULE" (defaults to view).
      const parts = this.permissionInput.split(':');
      moduleKey = parts[0];
      if (parts.length > 1 && parts[1]) {
        action = parts[1];
      }
    } else {
      moduleKey = this.permissionInput.module;
      action = this.permissionInput.action || 'view';
    }

    return this.permissionService.hasPermission(moduleKey, action);
  }
}
