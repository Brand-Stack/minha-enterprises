import { Component, OnInit } from '@angular/core';
import { ApiService } from './core/services/api.service';

@Component({
  selector: 'app-root',
  template: `
    <router-outlet></router-outlet>
    <app-toast></app-toast>
  `,
  styles: []
})
export class AppComponent implements OnInit {
  title = 'mybuddy';

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    const savedTheme = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const isDark = savedTheme === 'dark' || (!savedTheme && prefersDark);
    
    if (isDark) {
      document.documentElement.classList.add('dark');
    }
    
    // Try to load company name (will fail silently if not authenticated)
    this.loadCompanyName();
  }
  
  loadCompanyName() {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        if (settings?.companyName) {
          this.title = settings.companyName;
          document.title = settings.companyName;
        }
      },
      error: () => {
        // Keep default if not authenticated or settings not available
      }
    });
  }
}
