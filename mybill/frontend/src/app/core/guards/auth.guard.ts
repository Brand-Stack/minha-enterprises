import { Injectable } from '@angular/core';
import { CanActivate, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const isAuthenticated = this.authService.isAuthenticated();
    const token = this.authService.getToken();
    
    console.log('AuthGuard check - Route:', state.url);
    console.log('AuthGuard check - Token exists:', !!token);
    console.log('AuthGuard check - isAuthenticated:', isAuthenticated);
    
    if (isAuthenticated && token) {
      console.log('AuthGuard - Access granted');
      return true;
    }
    
    console.log('AuthGuard - Access denied, redirecting to login');
    this.router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }
}

