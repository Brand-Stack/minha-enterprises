import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const currentUser = this.authService.getCurrentUser();
    
    if (!currentUser || !currentUser.role) {
      this.router.navigate(['/login']);
      return false;
    }

    const requiredRoles = route.data['roles'] as string[];
    
    if (!requiredRoles || requiredRoles.length === 0) {
      return true; // No role requirement
    }

    const userRole = currentUser.role.toUpperCase();
    const hasAccess = requiredRoles.some(role => {
      const normalizedRole = role.toUpperCase();
      // Admin has access to everything
      if (userRole === 'ADMIN') {
        return true;
      }
      // Check exact match or role mapping
      if (userRole === normalizedRole) {
        return true;
      }
      // Map legacy roles
      if (normalizedRole === 'BILLING_USER' && (userRole === 'BILLER' || userRole === 'ADMIN')) {
        return true;
      }
      if (normalizedRole === 'PARTY_USER' && (userRole === 'PARTY_USER' || userRole === 'ADMIN')) {
        return true;
      }
      return false;
    });

    if (!hasAccess) {
      this.router.navigate(['/dashboard']);
      return false;
    }

    return true;
  }
}
