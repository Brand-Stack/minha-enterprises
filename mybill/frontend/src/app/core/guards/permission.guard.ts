import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { PermissionService } from '../services/permission.service';

/**
 * Route guard that enforces entitlement-based access. Routes declare the module /
 * action they require via {@code data.permission = { module, action }}.
 *
 * <p>Replaces the legacy {@code RoleGuard}. ADMIN bypasses all checks. Users
 * lacking the required permission are redirected to {@code /403}.</p>
 */
@Injectable({
  providedIn: 'root'
})
export class PermissionGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private permissionService: PermissionService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
      return false;
    }

    const required = route.data['permission'] as { module: string; action?: string } | undefined;

    // No declared permission requirement — authentication alone is sufficient.
    if (!required || !required.module) {
      return true;
    }

    const action = required.action || 'view';
    if (this.permissionService.hasPermission(required.module, action)) {
      return true;
    }

    this.router.navigate(['/403']);
    return false;
  }
}
